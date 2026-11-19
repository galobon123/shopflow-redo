package com.Proyect.Ecommerce.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Proyect.Ecommerce.controller.ProductController;
import com.Proyect.Ecommerce.service.ProductService;

/** HU-03 · Formato de error único. */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @RestController
    static class FailingController {
        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secreto interno del servidor");
        }

        @GetMapping("/missing")
        void missing() {
            throw new NotFoundException("Producto no encontrado: 9999");
        }

        @GetMapping("/duplicate")
        void duplicate() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/login")
        void login() {
            throw new BadCredentialsException("Bad credentials");
        }

        @GetMapping("/admin")
        void admin() {
            throw new AccessDeniedException("Access Denied");
        }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new FailingController(), new ProductController(mock(ProductService.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void validationErrorHasDetailPerField() throws Exception {
        String body = """
                {"name": "Remera", "price": -5, "stock": 3, "categoryId": 1}
                """;

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.errors.price").value("El precio debe ser mayor a 0"));
    }

    @Test
    void unexpectedErrorReturnsErrorIdWithoutStacktrace(CapturedOutput output) throws Exception {
        MvcResult result = mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorId").isString())
                .andExpect(content().string(not(containsString("secreto interno"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andReturn();

        String errorId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.errorId");
        assertTrue(output.getAll().contains("errorId=" + errorId));
        assertTrue(output.getAll().contains("secreto interno del servidor"));
    }

    @Test
    void notFoundKeepsTheDomainMessage() throws Exception {
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Producto no encontrado: 9999"));
    }

    @Test
    void integrityViolationIsAConflict() throws Exception {
        mvc.perform(get("/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("duplicate key"))));
    }

    @Test
    void badCredentialsAndAccessDenied() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Email o contraseña incorrectos."));
        mvc.perform(get("/admin"))
                .andExpect(status().isForbidden());
    }

    @Test
    void springErrorsUseTheSameFormat() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists());
    }
}
