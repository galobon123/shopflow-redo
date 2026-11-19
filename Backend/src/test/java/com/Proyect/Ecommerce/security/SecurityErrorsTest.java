package com.Proyect.Ecommerce.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.Proyect.Ecommerce.controller.ProductController;
import com.Proyect.Ecommerce.exception.GlobalExceptionHandler;
import com.Proyect.Ecommerce.service.ProductService;

/** HU-03 · 401/403 from the security filters use the same ProblemDetail format. */
@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, CorsConfig.class, RestAuthenticationHandlers.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:4200")
class SecurityErrorsTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void anonymousGets401ProblemDetail() throws Exception {
        mvc.perform(delete("/api/products/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Tenés que iniciar sesión para continuar."));
    }

    @Test
    void customerGets403ProblemDetail() throws Exception {
        mvc.perform(delete("/api/products/1").with(user("lucia@example.com").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No tenés permiso para hacer esto."));
    }
}
