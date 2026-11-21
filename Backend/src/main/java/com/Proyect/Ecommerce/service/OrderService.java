package com.Proyect.Ecommerce.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.Proyect.Ecommerce.dto.address.AddressResponseDTO;
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
import com.Proyect.Ecommerce.model.Product;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.AddressRepository;
import com.Proyect.Ecommerce.repository.OrderRepository;
import com.Proyect.Ecommerce.repository.ProductRepository;
import com.Proyect.Ecommerce.repository.UserRepository;

@Service
public class OrderService {

    private static final String CART_KEY_PREFIX = "cart:";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        AddressRepository addressRepository,
                        UserRepository userRepository,
                        StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public OrderResponseDTO createOrder(Long userId, OrderRequestDTO dto) {
        String cartKey = CART_KEY_PREFIX + userId;
        Map<Object, Object> rawCart = redisTemplate.opsForHash().entries(cartKey);

        if (rawCart.isEmpty()) {
            throw new BadRequestException("El carrito está vacío");
        }

        Set<Long> productIds = new HashSet<>();
        for (Object k : rawCart.keySet()) {
            try {
                productIds.add(Long.parseLong(k.toString()));
            } catch (NumberFormatException ignored) {
            }
        }

        if (productIds.isEmpty()) {
            throw new BadRequestException("El carrito está vacío");
        }

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + userId));
        // HU-31 · Checked here, against the database: the JWT's claim can be older than the click on the link.
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Confirmá tu email para comprar. Te mandamos un enlace al registrarte.");
        }

        Address address = addressRepository.findById(dto.getAddressId())
                .orElseThrow(() -> new NotFoundException("Dirección no encontrada: " + dto.getAddressId()));

        // Someone else's address answers like a missing one, so address ids don't leak (as in AddressService).
        if (!address.getCreatedBy().getId().equals(userId)) {
            throw new NotFoundException("Dirección no encontrada: " + dto.getAddressId());
        }

        Order order = new Order();
        order.setUser(user);
        order.setAddress(address);
        order.setItems(new ArrayList<>());

        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Object, Object> entry : rawCart.entrySet()) {
            Long productId;
            int quantity;

            try {
                productId = Long.parseLong(entry.getKey().toString());
                quantity = Integer.parseInt(entry.getValue().toString());
            } catch (NumberFormatException e) {
                continue;
            }

            Product product = productMap.get(productId);

            if (product == null || !product.isActive()) {
                continue;
            }

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

        if (order.getItems().isEmpty()) {
            throw new BadRequestException("El carrito no contiene productos válidos");
        }

        order.setTotal(total);
        Order savedOrder = orderRepository.save(order);

        redisTemplate.delete(cartKey);

        return toResponseDTO(savedOrder);
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
