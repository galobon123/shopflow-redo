package com.Proyect.Ecommerce.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.Proyect.Ecommerce.IntegrationTest;
import com.Proyect.Ecommerce.model.Role;
import com.Proyect.Ecommerce.model.User;
import com.Proyect.Ecommerce.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

/** HU-16 · Direcciones con provincia, ciudad y CPA, against the real Postgres seed (V2). */
@IntegrationTest
@AutoConfigureMockMvc
class AddressIntegrationTest {

    private static final int CORDOBA = 14;
    private static final int SANTA_FE = 82;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;

    private User ana;
    private User beto;

    @BeforeEach
    void emptyStore() {
        jdbc.execute("TRUNCATE reviews, order_items, orders, address, wishlist, product_images, products, categories, users RESTART IDENTITY CASCADE");
        ana = customer("Ana");
        beto = customer("Beto");
    }

    /** Escenario: Cargar y reutilizar una dirección. */
    @Test
    void savesTheCpaInCapitalsAndListsItInMyAddresses() throws Exception {
        long id = create(ana, CORDOBA, city(CORDOBA, "Villa Carlos Paz"), "Av. San Martín 123", "x5152abc");

        mvc.perform(get("/api/addresses").with(as(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].address").value("Av. San Martín 123"))
                .andExpect(jsonPath("$[0].cityName").value("Villa Carlos Paz"))
                .andExpect(jsonPath("$[0].stateName").value("Córdoba"))
                .andExpect(jsonPath("$[0].zipCode").value("X5152ABC"));
    }

    @Test
    void acceptsTheOldFourDigitCodeAndDropsSpaces() throws Exception {
        create(ana, SANTA_FE, city(SANTA_FE, "Rosario"), "Bv. Oroño 845", " 2000 ");
        create(ana, CORDOBA, city(CORDOBA, "Córdoba"), "Obispo Trejo 312", "x 5000 abc");

        mvc.perform(get("/api/addresses").with(as(ana)))
                .andExpect(jsonPath("$[*].zipCode", hasItem("2000")))
                .andExpect(jsonPath("$[*].zipCode", hasItem("X5000ABC")));
    }

    /** Escenario: CPA inválido. */
    @Test
    void anInvalidPostalCodeIsRefusedOnTheField() throws Exception {
        for (String zip : List.of("ABC", "123", "X5152AB", "55152ABC")) {
            mvc.perform(post("/api/addresses").with(as(ana))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(CORDOBA, city(CORDOBA, "Villa Carlos Paz"), "Av. San Martín 123", zip)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.zipCode").value("Código postal inválido"));
        }
        assertThat(count("SELECT COUNT(*) FROM address")).isZero();
    }

    @Test
    void theCityHasToBelongToTheChosenProvince() throws Exception {
        mvc.perform(post("/api/addresses").with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SANTA_FE, city(CORDOBA, "Villa Carlos Paz"), "Av. San Martín 123", "X5152ABC")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Elegí una ciudad de la provincia elegida."));
        assertThat(count("SELECT COUNT(*) FROM address")).isZero();
    }

    /** Escenario: Dirección de otro cliente. Same answer as one that doesn't exist, for every verb. */
    @Test
    void someoneElsesAddressAnswersLikeAMissingOne() throws Exception {
        long betos = create(beto, SANTA_FE, city(SANTA_FE, "Rosario"), "Pellegrini 1650", "2000");
        String missing = mvc.perform(delete("/api/addresses/{id}", 999).with(as(ana)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String detail = JsonPath.read(missing, "$.detail");

        mvc.perform(delete("/api/addresses/{id}", betos).with(as(ana)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(detail));
        mvc.perform(get("/api/addresses/{id}", betos).with(as(ana)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(detail));
        mvc.perform(put("/api/addresses/{id}", betos).with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SANTA_FE, city(SANTA_FE, "Rosario"), "Otra 1", "2000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(detail));

        mvc.perform(get("/api/addresses/{id}", betos).with(as(beto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("Pellegrini 1650"));
    }

    /** Deleting an address an order points to hides it from "Mis direcciones"; the order keeps it. */
    @Test
    void deletingAnAddressUsedByAnOrderKeepsTheOrderWhole() throws Exception {
        long id = create(ana, SANTA_FE, city(SANTA_FE, "Rosario"), "Bv. Oroño 845", "2000");
        long order = orderAt(ana, id, "2026-11-01 10:00");

        mvc.perform(delete("/api/addresses/{id}", id).with(as(ana))).andExpect(status().isNoContent());

        mvc.perform(get("/api/addresses").with(as(ana))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/addresses/{id}", id).with(as(ana))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/addresses/{id}", id).with(as(ana))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT a.address FROM orders o JOIN address a ON a.id = o.address_id WHERE o.id = ?",
                String.class, order)).isEqualTo("Bv. Oroño 845");
    }

    /** Editing an address an order points to saves a new one: the order still shows where it was sent. */
    @Test
    void editingAnAddressUsedByAnOrderLeavesTheOrderAsItWas() throws Exception {
        long id = create(ana, SANTA_FE, city(SANTA_FE, "Rosario"), "Bv. Oroño 845", "2000");
        long order = orderAt(ana, id, "2026-11-01 10:00");

        mvc.perform(put("/api/addresses/{id}", id).with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SANTA_FE, city(SANTA_FE, "Rosario"), "Pellegrini 1650", "s2000abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("Pellegrini 1650"))
                .andExpect(jsonPath("$.zipCode").value("S2000ABC"));

        mvc.perform(get("/api/addresses").with(as(ana)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].address").value("Pellegrini 1650"));
        assertThat(jdbc.queryForObject("SELECT a.address FROM orders o JOIN address a ON a.id = o.address_id WHERE o.id = ?",
                String.class, order)).isEqualTo("Bv. Oroño 845");
    }

    /** Before HU-16 the zip code could be 1 to 4 digits: those addresses can still be deleted and edited. */
    @Test
    void anAddressSavedBeforeTheCpaRuleCanBeDeletedAndEdited() throws Exception {
        long toDelete = legacyAddress(ana, "123");
        long toEdit = legacyAddress(ana, "12");
        long order = orderAt(ana, toEdit, "2026-11-01 10:00");

        mvc.perform(delete("/api/addresses/{id}", toDelete).with(as(ana))).andExpect(status().isNoContent());
        mvc.perform(put("/api/addresses/{id}", toEdit).with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(SANTA_FE, city(SANTA_FE, "Rosario"), "Pellegrini 1650", "s2000abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zipCode").value("S2000ABC"));

        mvc.perform(get("/api/addresses").with(as(ana)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].address").value("Pellegrini 1650"));
        assertThat(jdbc.queryForObject("SELECT a.zip_code FROM orders o JOIN address a ON a.id = o.address_id WHERE o.id = ?",
                String.class, order)).isEqualTo("12");
    }

    @Test
    void editingAnUnusedAddressKeepsItsId() throws Exception {
        long id = create(ana, SANTA_FE, city(SANTA_FE, "Rosario"), "Bv. Oroño 845", "2000");

        mvc.perform(put("/api/addresses/{id}", id).with(as(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CORDOBA, city(CORDOBA, "Villa Carlos Paz"), "Av. San Martín 123", "x5152abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.cityName").value("Villa Carlos Paz"));
    }

    /** The checkout preselects the first one: the last used, then the newest. */
    @Test
    void myAddressesComeLastUsedFirst() throws Exception {
        long casa = create(ana, SANTA_FE, city(SANTA_FE, "Rosario"), "Casa 1", "2000");
        long trabajo = create(ana, SANTA_FE, city(SANTA_FE, "Rosario"), "Trabajo 2", "2000");
        long nueva = create(ana, SANTA_FE, city(SANTA_FE, "Rosario"), "Nueva 3", "2000");
        orderAt(ana, trabajo, "2026-10-01 10:00");
        orderAt(ana, casa, "2026-11-01 10:00");

        mvc.perform(get("/api/addresses").with(as(ana)))
                .andExpect(jsonPath("$[0].id").value(casa))
                .andExpect(jsonPath("$[1].id").value(trabajo))
                .andExpect(jsonPath("$[2].id").value(nueva));
    }

    @Test
    void addressesNeedASession() throws Exception {
        mvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
    }

    @Test
    void provincesArePublicCacheableAndSortedWithAccents() throws Exception {
        String json = mvc.perform(get("/api/states"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(jsonPath("$", hasSize(24)))
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(json, "$[*].name");
        assertThat(names.indexOf("Córdoba")).isLessThan(names.indexOf("Corrientes"));
        assertThat(names.indexOf("Entre Ríos")).isLessThan(names.indexOf("Formosa"));
        assertThat(names.get(0)).isEqualTo("Buenos Aires");
    }

    @Test
    void citiesOfAProvinceArePublicCacheableAndSortedWithAccents() throws Exception {
        String json = mvc.perform(get("/api/cities").param("stateId", String.valueOf(CORDOBA)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
                .andExpect(jsonPath("$[*].name", hasItem("Villa Carlos Paz")))
                .andReturn().getResponse().getContentAsString();

        List<String> names = JsonPath.read(json, "$[*].name");
        assertThat(names).doesNotContain("Rosario");
        // V7 · No soft hyphens left from the source data, and "Río Tercero" sorts among the R, not after the Z.
        assertThat(names).contains("Río Tercero").noneMatch(name -> name.contains("­"));
        assertThat(names.indexOf("Río Tercero")).isLessThan(names.indexOf("Saldán"));
    }

    @Test
    void citiesNeedTheProvince() throws Exception {
        mvc.perform(get("/api/cities")).andExpect(status().isBadRequest());
    }

    private long create(User owner, int stateId, int cityId, String street, String zip) throws Exception {
        String json = mvc.perform(post("/api/addresses").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(stateId, cityId, street, zip)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /** Straight to the table, as the old rule let it in. */
    private long legacyAddress(User owner, String zip) {
        return jdbc.queryForObject("INSERT INTO address (first_name, last_name, address, city_id, zip_code, created_by) "
                + "VALUES ('Ana', 'Gómez', 'Bv. Oroño 845', ?, ?, ?) RETURNING id", Long.class,
                city(SANTA_FE, "Rosario"), zip, owner.getId());
    }

    private long orderAt(User owner, long addressId, String createdAt) {
        return jdbc.queryForObject("INSERT INTO orders (created_at, user_id, total, address_id, status) "
                + "VALUES (CAST(? AS timestamp), ?, 0, ?, 'PAGADA') RETURNING id", Long.class, createdAt, owner.getId(), addressId);
    }

    private static String body(int stateId, int cityId, String street, String zip) {
        return """
                {"firstName": "Ana", "lastName": "Gómez", "address": "%s", "stateId": %d, "cityId": %d, "zipCode": "%s"}
                """.formatted(street, stateId, cityId, zip);
    }

    private int city(int stateId, String name) {
        return jdbc.queryForObject("SELECT id FROM city WHERE state_id = ? AND name = ?", Integer.class, stateId, name);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private static RequestPostProcessor as(User user) {
        return authentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), user.getId(), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private User customer(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail(name.toLowerCase() + "@example.com");
        user.setPassword("not-used");
        user.setRole(Role.USER);
        user.setEmailVerified(true);
        return users.save(user);
    }
}
