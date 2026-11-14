package com.oficina.billing.infrastructure.web.dto.request;

import com.oficina.billing.domain.valueobject.DecisaoOrcamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DecisaoRequest(
    @NotBlank String token,
    @NotNull DecisaoOrcamento decisao
) {}
