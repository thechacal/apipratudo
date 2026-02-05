package com.apipratudo.identity.service;

import com.apipratudo.identity.dto.CnpjStatusResponse;
import com.apipratudo.identity.dto.DocumentValidateRequest;
import com.apipratudo.identity.dto.DocumentValidateResponse;
import com.apipratudo.identity.dto.VerificationRequest;
import com.apipratudo.identity.dto.VerificationResponse;
import com.apipratudo.identity.error.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IdentityService {

  private final Clock clock;

  public IdentityService() {
    this(Clock.systemUTC());
  }

  IdentityService(Clock clock) {
    this.clock = clock;
  }

  public DocumentValidateResponse validarDocumento(DocumentValidateRequest request) {
    DocumentValidator.ValidationResult result = DocumentValidator.validate(request.tipo(), request.documento());
    if (!"CPF".equals(result.tipo()) && !"CNPJ".equals(result.tipo())) {
      throw new ApiException(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "tipo deve ser CPF ou CNPJ");
    }
    return new DocumentValidateResponse(
        result.tipo(),
        result.documentoNormalizado(),
        result.validoEstrutural(),
        result.motivos()
    );
  }

  public CnpjStatusResponse consultarCnpj(String cnpj) {
    DocumentValidator.ValidationResult result = DocumentValidator.validate("CNPJ", cnpj);
    String status = result.validoEstrutural() ? "UNKNOWN" : "INVALID";
    return new CnpjStatusResponse(
        result.documentoNormalizado(),
        result.validoEstrutural(),
        status,
        null,
        "OFFLINE",
        Instant.now(clock)
    );
  }

  public VerificationResponse verificar(VerificationRequest request) {
    String normalizedContext = request.contexto().trim().toLowerCase(Locale.ROOT);
    DocumentValidator.ValidationResult document = DocumentValidator.validate(
        request.documento().tipo(),
        request.documento().valor()
    );
    if (!"CPF".equals(document.tipo()) && !"CNPJ".equals(document.tipo())) {
      throw new ApiException(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", "tipo deve ser CPF ou CNPJ");
    }

    boolean attrsProvided = hasOptionalAttributes(request.atributos_opcionais());
    String cnpjStatus = "CNPJ".equals(document.tipo()) ? (document.validoEstrutural() ? "UNKNOWN" : "INVALID") : "N/A";

    boolean invalidDocument = !document.validoEstrutural();
    List<VerificationResponse.Risco> riscos = new ArrayList<>();
    riscos.add(new VerificationResponse.Risco("documento_invalido", 100, invalidDocument));

    String veredito;
    List<String> proximoPassos = new ArrayList<>();
    if (invalidDocument) {
      veredito = "NEGAR";
    } else if ("anti_fraude".equals(normalizedContext) && attrsProvided) {
      veredito = "REVISAR";
      proximoPassos.add("validar_identidade_com_provedor_externo");
    } else {
      veredito = "APROVAR";
    }

    VerificationResponse.Sinais sinais = new VerificationResponse.Sinais(
        document.validoEstrutural(),
        document.tipo(),
        cnpjStatus,
        attrsProvided
    );

    return new VerificationResponse(veredito, sinais, riscos, proximoPassos);
  }

  private boolean hasOptionalAttributes(VerificationRequest.AtributosOpcionais attrs) {
    if (attrs == null) {
      return false;
    }
    return StringUtils.hasText(attrs.nome())
        || StringUtils.hasText(attrs.nascimento())
        || StringUtils.hasText(attrs.email())
        || StringUtils.hasText(attrs.telefone());
  }
}
