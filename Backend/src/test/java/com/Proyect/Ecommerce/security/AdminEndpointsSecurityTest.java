package com.Proyect.Ecommerce.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.Proyect.Ecommerce.controller.AdminProductController;
import com.Proyect.Ecommerce.controller.AdminReviewController;
import com.Proyect.Ecommerce.controller.CategoryController;
import com.Proyect.Ecommerce.controller.ProductController;
import com.Proyect.Ecommerce.controller.UserController;
import com.Proyect.Ecommerce.exception.GlobalExceptionHandler;
import com.Proyect.Ecommerce.service.CategoryService;
import com.Proyect.Ecommerce.service.ProductService;
import com.Proyect.Ecommerce.service.RecommendedProducts;
import com.Proyect.Ecommerce.service.ReviewService;
import com.Proyect.Ecommerce.service.UserService;

/**
 * HU-27 · Admin-only endpoints: a guest gets 401, a customer 403, an admin gets through.
 * Moves to /api/admin/** with HU-21; this list is the contract to keep.
 */
@WebMvcTest({ProductController.class, AdminProductController.class, AdminReviewController.class,
        CategoryController.class, UserController.class})
@Import({SecurityConfig.class, CorsConfig.class, RestAuthenticationHandlers.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:4200")
class AdminEndpointsSecurityTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private ProductService productService;
    @MockitoBean private RecommendedProducts recommendedProducts;
    @MockitoBean private ReviewService reviewService;
    @MockitoBean private CategoryService categoryService;
    @MockitoBean private UserService userService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsServiceImpl userDetailsService;
    @MockitoBean private SessionService sessionService;

    static Stream<Arguments> adminEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/admin/products"),
                Arguments.of(HttpMethod.POST, "/api/products"),
                Arguments.of(HttpMethod.PUT, "/api/products/1/deactivate"),
                Arguments.of(HttpMethod.PUT, "/api/admin/reviews/1/hide"),
                Arguments.of(HttpMethod.PUT, "/api/admin/reviews/1/show"),
                Arguments.of(HttpMethod.POST, "/api/categories"),
                Arguments.of(HttpMethod.DELETE, "/api/categories/1"),
                Arguments.of(HttpMethod.GET, "/api/users"),
                Arguments.of(HttpMethod.GET, "/api/users/1"),
                Arguments.of(HttpMethod.DELETE, "/api/users/1"));
    }

    private static MockHttpServletRequestBuilder call(HttpMethod method, String url) {
        return request(method, url).contentType(MediaType.APPLICATION_JSON).content("{}");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminEndpoints")
    void guestGets401(HttpMethod method, String url) throws Exception {
        mvc.perform(call(method, url)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminEndpoints")
    void customerGets403(HttpMethod method, String url) throws Exception {
        mvc.perform(call(method, url).with(user("lucia@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("adminEndpoints")
    void adminGetsThrough(HttpMethod method, String url) throws Exception {
        int status = mvc.perform(call(method, url).with(user("admin@example.com").roles("ADMIN")))
                .andReturn().getResponse().getStatus();
        // Past security: the controller answers (2xx, or 400 because the empty body is invalid).
        assertTrue(status != 401 && status != 403, "admin blocked with " + status);
    }
}
