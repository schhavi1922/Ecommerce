package com.infominez.ecommerce.health;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
class HealthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean HealthEndpoint endpoint;

    @Test void livenessIsIndependentOfReadiness() throws Exception {
        when(endpoint.healthForPath("liveness")).thenReturn(Health.up().build());
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        verify(endpoint, never()).healthForPath("readiness");
    }
    @Test void readyWhenDatabaseIsAvailable() throws Exception {
        when(endpoint.healthForPath("readiness")).thenReturn(Health.up().build());
        mvc.perform(get("/ready")).andExpect(status().isOk());
    }
    @Test void unavailableWhenDatabaseIsDown() throws Exception {
        when(endpoint.healthForPath("readiness")).thenReturn(Health.down().build());
        mvc.perform(get("/ready")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
}
