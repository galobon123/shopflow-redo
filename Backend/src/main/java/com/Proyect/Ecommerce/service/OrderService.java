package com.Proyect.Ecommerce.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.Proyect.Ecommerce.dto.PageResponse;
import com.Proyect.Ecommerce.dto.address.AddressResponseDTO;
import com.Proyect.Ecommerce.dto.cart.CartItemDTO;
import com.Proyect.Ecommerce.dto.order.OrderItemDTO;
import com.Proyect.Ecommerce.dto.order.OrderRequestDTO;
import com.Proyect.Ecommerce.dto.order.OrderResponseDTO;
import com.Proyect.Ecommerce.exception.BadRequestException;
import com.Proyect.Ecommerce.exception.ConflictException;
import com.Proyect.Ecommerce.exception.ForbiddenException;
import com.Proyect.Ecommerce.exception.NotFoundException;
import com.Proyect.Ecommerce.model.Address;
import com.Proyect.Ecommerce.model.Order;
import com.Proyect.Ecommerce.model.OrderItem;
import com.Proyect.Ecommerce.model.OrderStatus;
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.AddressRepository;
import com.Proyect.Ecommerce.repository.OrderRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.ReviewRepository;
import com.Proyect.Ecommerce.repository.UserRepository;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** HU-17 · How long an unpaid order holds its stock (HU-18 cancels it after that). */
    static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

    /** HU-19 · Orders per page of "Mis pedidos". */
    public static final int PAGE_SIZE = 10;
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;
    private final CartService cartService;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        AddressRepository addressRepository,
                        UserRepository userRepository,
                        ReviewRepository reviewRepository,
                        CartService cartService,
                        ApplicationEventPublisher events) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.reviewRepository = reviewRepository;
        this.cartService = cartService;
        this.events = events;
    }

    /**
     * HU-17 · Takes the stock and saves the order, all or nothing, with the prices and total worked out here
     * (prices already include IVA). The order waits for its payment for {@link #PAYMENT_WINDOW}.
     * The cart is emptied only once the order is committed, so a failure leaves it as it was.
     * Called through OrderCheckout, which makes the request idempotent.
     */
    @Transactional
    public OrderResponseDTO createOrder(Long userId, OrderRequestDTO dto) {
        // HU-13 · The cart is read through CartService, the only owner of the Redis keys.
        List<CartItemDTO> cart = cartService.getCart(CartService.customer(userId));

        if (cart.isEmpty()) {
            throw new BadRequestException("El carrito está vacío");
        }

        // HU-13 · Nothing is skipped in silence: an unavailable product blocks the purchase until it's taken out.
        for (CartItemDTO line : cart) {
            if (!line.isAvailable()) {
                throw new ConflictException(line.getProductName()
                        + " ya no está disponible. Sacá ese producto del carrito para seguir.");
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + userId));
        // HU-31 · Checked here, against the database: the JWT's claim can be older than the click on the link.
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Confirmá tu email para comprar. Te mandamos un enlace al registrarte.");
        }

        // Someone else's address answers like a missing one, so address ids don't leak (as in AddressService).
        // HU-16 · A deleted one too: it stays only for the orders already sent there.
        Address address = addressRepository.findByIdAndCreatedBy_IdAndDeletedAtIsNull(dto.getAddressId(), userId)
                .orElseThrow(() -> new NotFoundException("Dirección no encontrada: " + dto.getAddressId()));

        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setItems(new ArrayList<>());
        order.setStatus(OrderStatus.PENDIENTE_PAGO);
        order.setExpiresAt(LocalDateTime.now().plus(PAYMENT_WINDOW));

        BigDecimal total = BigDecimal.ZERO;

        for (CartItemDTO line : cart) {
            Long productId = line.getProductId();
            int quantity = line.getQuantity();
            // Already loaded by getCart in this transaction: no second query.
            Product product = productRepository.getReferenceById(productId);

            // Check and decrement in one UPDATE: two buyers can't both take the last unit.
            // Throwing rolls back the decrements already made for earlier items.
            if (productRepository.decrementStock(productId, quantity) == 0) {
                throw new ConflictException("Sin stock de " + product.getName() + ".");
            }

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(product.getPrice());
            order.getItems().add(item);

            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        order.setTotal(total);
        Order savedOrder = orderRepository.save(order);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // The order is already saved: a Redis hiccup here must not turn it into an error.
                try {
                    cartService.clearCart(CartService.customer(userId));
                } catch (RuntimeException e) {
                    log.warn("Order {} saved but the cart of user {} could not be emptied", savedOrder.getId(), userId, e);
                }
            }
        });

        return toResponseDTO(savedOrder, Set.of());
    }

    /**
     * HU-17 · One of the customer's orders; someone else's answers like a missing one.
     * HU-19 · The detail in "Mis pedidos" and the receipt after buying.
     */
    @Transactional(readOnly = true)
    public OrderResponseDTO findOwn(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new NotFoundException("Pedido no encontrado."));
        return toResponseDTO(order, reviewedProducts(userId, List.of(order)));
    }

    /**
     * HU-19 · "Mis pedidos": one page of the customer's orders, newest first. The same few queries whatever the
     * page holds: the page of ids (and its count), those orders with their items, and their products' photos.
     */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponseDTO> findPage(Long userId, int page) {
        if (page < 0) {
            throw new BadRequestException("La página no puede ser negativa.");
        }
        Page<Long> ids = orderRepository.findIdsByUserId(userId, PageRequest.of(page, PAGE_SIZE, NEWEST));
        Map<Long, Order> orders = ids.isEmpty() ? Map.of() : orderRepository.findByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        Set<Long> reviewed = reviewedProducts(userId, orders.values());
        return PageResponse.of(ids, id -> toResponseDTO(orders.get(id), reviewed));
    }

    /**
     * HU-15 · An order waiting for its payment becomes PAGADA. HU-18's approved-payment webhook is meant to call
     * this; nothing else does yet. After the commit its purchases go to Neo4j (GraphSync), which also forgets
     * the customer's cached recommendations, and the confirmation email is queued (HU-20, OrderMails).
     */
    @Transactional
    public void markPaid(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));
        if (order.getStatus() != OrderStatus.PENDIENTE_PAGO) {
            throw new ConflictException("Esta orden ya no está esperando el pago.");
        }
        order.setStatus(OrderStatus.PAGADA);
        events.publishEvent(new OrderStatusChanged(orderId, OrderStatus.PAGADA));
    }

    /** HU-19 · Products of these orders' delivered ones that the customer already reviewed: one query, none without any. */
    private Set<Long> reviewedProducts(Long userId, Collection<Order> orders) {
        List<Long> delivered = orders.stream()
                .filter(order -> order.getStatus() == OrderStatus.ENTREGADA)
                .flatMap(order -> order.getItems().stream())
                .map(item -> item.getProduct().getId())
                .distinct()
                .toList();
        return delivered.isEmpty() ? Set.of() : reviewRepository.findReviewedProductIds(userId, delivered);
    }

    private OrderResponseDTO toResponseDTO(Order order, Set<Long> reviewed) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setCreatedAt(instant(order.getCreatedAt()));
        dto.setTotal(order.getTotal());
        dto.setStatus(order.getStatus());
        dto.setExpiresAt(order.getExpiresAt() == null ? null : instant(order.getExpiresAt()));
        dto.setAddress(toAddressDTO(order.getAddress()));
        boolean delivered = order.getStatus() == OrderStatus.ENTREGADA;
        dto.setItems(order.getItems().stream().map(item -> toItemDTO(item, delivered, reviewed)).toList());
        return dto;
    }

    private OrderItemDTO toItemDTO(OrderItem item, boolean delivered, Set<Long> reviewed) {
        Product product = item.getProduct();
        OrderItemDTO dto = new OrderItemDTO();
        dto.setProductId(product.getId());
        dto.setProductName(product.getName());
        dto.setImageUrl(product.getImageUrl());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        // Same rule as ReviewService.create: received, still for sale and not reviewed yet.
        dto.setCanReview(delivered && product.isActive() && !reviewed.contains(product.getId()));
        return dto;
    }

    private AddressResponseDTO toAddressDTO(Address address) {
        AddressResponseDTO dto = new AddressResponseDTO();
        dto.setId(address.getId());
        dto.setFirstName(address.getFirstName());
        dto.setLastName(address.getLastName());
        dto.setAddress(address.getAddress());
        dto.setStateId(address.getCity().getState().getId());
        dto.setCityId(address.getCity().getId());
        dto.setCityName(address.getCity().getName());
        dto.setStateName(address.getCity().getState().getName());
        dto.setZipCode(address.getZipCode());
        return dto;
    }

    /** Dates are the JVM's local time, which EcommerceApplication fixes to UTC (as in ReviewService). */
    private static Instant instant(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant();
    }
}
