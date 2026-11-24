package com.Proyect.Ecommerce.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.Proyect.Ecommerce.repository.UserRepository;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        AddressRepository addressRepository,
                        UserRepository userRepository,
                        CartService cartService,
                        ApplicationEventPublisher events) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.cartService = cartService;
        this.events = events;
    }

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

        cartService.clearCart(CartService.customer(userId));

        return toResponseDTO(savedOrder);
    }

    /**
     * HU-15 · An order waiting for its payment becomes PAGADA. HU-18's approved-payment webhook is meant to call
     * this; nothing else does yet. After the commit its purchases go to Neo4j (GraphSync), which also forgets
     * the customer's cached recommendations.
     */
    @Transactional
    public void markPaid(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Orden no encontrada: " + orderId));
        if (order.getStatus() != OrderStatus.PENDIENTE_PAGO) {
            throw new ConflictException("Esta orden ya no está esperando el pago.");
        }
        order.setStatus(OrderStatus.PAGADA);
        events.publishEvent(new OrderStatusChanged(orderId));
    }

    public List<OrderResponseDTO> findByUserId(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    private OrderResponseDTO toResponseDTO(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setTotal(order.getTotal());
        dto.setAddress(toAddressDTO(order.getAddress()));
        dto.setItems(order.getItems().stream().map(this::toItemDTO).toList());
        return dto;
    }

    private OrderItemDTO toItemDTO(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setProductId(item.getProduct().getId());
        dto.setProductName(item.getProduct().getName());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setSubtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        return dto;
    }

    private AddressResponseDTO toAddressDTO(Address address) {
        AddressResponseDTO dto = new AddressResponseDTO();
        dto.setId(address.getId());
        dto.setFirstName(address.getFirstName());
        dto.setLastName(address.getLastName());
        dto.setAddress(address.getAddress());
        dto.setCityName(address.getCity().getName());
        dto.setStateName(address.getCity().getState().getName());
        dto.setZipCode(address.getZipCode());
        return dto;
    }
}
