package com.aisocialgame.controller;

import com.aisocialgame.exception.GlobalExceptionHandler;
import com.aisocialgame.service.AuthService;
import com.aisocialgame.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WalletController.class)
@Import(GlobalExceptionHandler.class)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private WalletService walletService;

    @Test
    void balanceWithoutTokenShouldReturnUnauthorized() throws Exception {
        when(authService.authenticate(null)).thenReturn(null);

        mockMvc.perform(get("/api/wallet/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("未登录"));

        verify(authService).authenticate(null);
        verifyNoInteractions(walletService);
    }
}
