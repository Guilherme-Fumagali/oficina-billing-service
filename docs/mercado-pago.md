# Mercado Pago: preparação da conta e testes

Este roteiro descreve a configuração necessária para que o Billing Service cobre orçamentos pelo Checkout Pro do Mercado Pago nos ambientes da AWS. A execução local não depende de conta: o perfil `local` usa o simulador descrito na seção 8.

## 1. Conta e painel de desenvolvedores

1. Criar (ou usar) uma conta do Mercado Pago no Brasil.
2. Acessar o painel de desenvolvedores em [mercadopago.com.br/developers/panel](https://www.mercadopago.com.br/developers/panel), menu **Suas integrações**.

## 2. Aplicação Checkout Pro

1. Em **Suas integrações**, selecionar **Criar aplicação**.
2. Informar o nome `oficina-billing`, o tipo de solução **Pagamentos on-line** e o produto **Checkout Pro**.
3. Concluir a criação. A aplicação passa a exibir as credenciais, a configuração de webhooks e a área de contas de teste.

## 3. Contas de teste (vendedor e comprador)

1. Na aplicação, abrir **Contas de teste** e criar duas contas com país **Brasil**:
   - `vendedor-oficina`, do tipo **Vendedor**;
   - `comprador-oficina`, do tipo **Comprador**, com saldo fictício suficiente para os testes.
2. Registrar, para cada conta, o usuário, a senha e o User ID exibidos no painel. Se o login de uma conta de teste pedir um código de verificação, seguir a orientação do próprio painel de contas de teste.

O vendedor de teste representa a oficina; o comprador de teste representa o cliente que paga o orçamento.

## 4. Credenciais do vendedor de teste

1. Em uma janela anônima, entrar no Mercado Pago com o usuário e a senha do **vendedor de teste**.
2. Acessar o painel de desenvolvedores com essa conta, criar (ou abrir) a aplicação Checkout Pro e copiar o **Access Token de produção** da conta de vendedor de teste. O valor começa com `APP_USR-`.

O serviço usa as credenciais do vendedor de teste, e não as credenciais de teste (`TEST-...`) da conta real, porque pagamentos criados com credenciais de teste não disparam webhooks. Mesmo com as credenciais corretas, o serviço não depende do webhook: a reconciliação consulta a cada minuto os pagamentos pendentes (seção 7).

## 5. Webhook e assinatura secreta

1. Ainda logado como vendedor de teste, abrir a aplicação e o menu **Webhooks** > **Configurar notificações**.
2. No modo **produção**, informar a URL `https://<api-gateway-url>/api/pagamentos/webhooks/mercado-pago` (o valor de `<api-gateway-url>` está no parâmetro SSM `/oficina/<ambiente>/api-gateway-url`) e marcar o evento **Pagamentos**.
3. Salvar. O painel gera a **assinatura secreta** usada no cabeçalho `x-signature`; copiar o valor.

A URL do API Gateway muda a cada provisionamento do ambiente. Por isso o serviço envia a `notification_url` em cada preferência, calculada a partir de `APP_URL_PUBLICA`, e a URL do painel não precisa ser atualizada a cada ensaio. A assinatura secreta permanece a mesma.

## 6. Secrets do repositório no GitHub

No repositório `oficina-billing-service`, em **Settings** > **Secrets and variables** > **Actions**, criar:

| Secret | Valor |
|---|---|
| `MP_ACCESS_TOKEN` | Access Token `APP_USR-...` do vendedor de teste (seção 4) |
| `MP_WEBHOOK_SECRET` | assinatura secreta do webhook (seção 5) |

Pela linha de comando, sem expor os valores no histórico do terminal:

```bash
gh secret set MP_ACCESS_TOKEN --repo Guilherme-Fumagali/oficina-billing-service
gh secret set MP_WEBHOOK_SECRET --repo Guilherme-Fumagali/oficina-billing-service
```

Os secrets seguem este caminho:

1. O workflow `infra.yml` (ação `apply`) os entrega ao Terraform como `TF_VAR_mp_access_token` e `TF_VAR_mp_webhook_secret`.
2. O Terraform grava os parâmetros `SecureString` `/oficina/<ambiente>/billing/mp-access-token` e `/oficina/<ambiente>/billing/mp-webhook-secret`.
3. O job de deploy do `ci-cd.yml` lê os parâmetros e cria o Secret `billing-service-secrets` no namespace `oficina`, com as chaves `MP_ACCESS_TOKEN` e `MP_WEBHOOK_SECRET`.

Os valores não aparecem no código, nos manifestos nem nos logs.

Além dos secrets, criar a **variável** `MP_EMAIL_PAGADOR_TESTE` com o e-mail do comprador de teste (seção 3). O deploy de homologação a coloca no Secret do serviço e o Billing a envia em `payer.email`, o que evita misturar usuário real e de teste na mesma cobrança. Em produção o valor fica vazio e vale o e-mail do cliente.

```bash
gh variable set MP_EMAIL_PAGADOR_TESTE --repo Guilherme-Fumagali/oficina-billing-service --body "<e-mail do comprador de teste>"
```

## 7. Como o serviço usa o Mercado Pago

| Etapa | Chamada |
|---|---|
| Aprovação do orçamento | `POST /checkout/preferences` com os itens, `external_reference` igual ao id do orçamento, `notification_url`, `binary_mode: true`, `expires: true`, `expiration_date_to` igual ao prazo de pagamento e cabeçalho `X-Idempotency-Key` |
| Notificação | `POST /api/pagamentos/webhooks/mercado-pago`: o serviço confere a assinatura e consulta `GET /v1/payments/{id}`; o corpo da notificação não é usado como fonte do status |
| Reconciliação (a cada 60 s) | `GET /v1/payments/search?external_reference={orcamentoId}` para os pagamentos pendentes criados há mais de 60 segundos |
| Prazo vencido (a cada 5 min) | última consulta ao Mercado Pago; se o pagamento continuar pendente, o serviço o marca como `EXPIRADO` e publica `PagamentoRecusado` com motivo `EXPIRADO` |
| Compensação | `POST /v1/payments/{id}/refunds` com corpo vazio (estorno total) e `X-Idempotency-Key` igual ao id do pagamento |

O link enviado ao cliente é o `init_point` da preferência. A variável `MP_USAR_SANDBOX_INIT_POINT=true` troca o link pelo `sandbox_init_point`, o que só faz sentido com credenciais `TEST-`.

Se o Mercado Pago enviar notificações sem o cabeçalho `x-signature` para a `notification_url` da preferência, o serviço responde `401` e registra a tentativa na tabela `notificacoes_pagamento`; o resultado do pagamento chega pela reconciliação em até um minuto. A exigência de assinatura pode ser desligada com `MP_EXIGIR_ASSINATURA=false`, o que não é recomendado fora do ambiente local.

## 8. Testes de pagamento

1. Aprovar um orçamento pela API ou pelo link do e-mail. A resposta da aprovação e o evento `OrcamentoAprovado` trazem o link de pagamento.
2. Abrir o link em uma janela anônima e entrar com a conta **comprador de teste**. Usar a mesma conta em todos os testes evita misturar usuários reais e de teste na mesma transação; com `MP_EMAIL_PAGADOR_TESTE` preenchido com o e-mail do comprador de teste, o serviço envia esse e-mail no campo `payer.email` da preferência.
3. Pagar com um dos cartões de teste abaixo, com qualquer data de validade futura informada no painel (por exemplo 11/30), CPF `12345678909` e o **nome do titular** que define o resultado.

| Bandeira | Número | Código de segurança |
|---|---|---|
| Mastercard | 5031 4332 1540 6351 | 123 |
| Visa | 4235 6477 2802 5682 | 123 |
| American Express | 3753 651535 56885 | 1234 |

| Nome do titular | Resultado no Mercado Pago | Efeito no Billing |
|---|---|---|
| `APRO` | pagamento aprovado | `PagamentoAprovado`; a OS segue para execução |
| `OTHE` | recusado por erro geral | `PagamentoRecusado` com o `status_detail`; a saga compensa |
| `FUND` | recusado por saldo insuficiente | `PagamentoRecusado` |
| `SECU` | recusado por código de segurança inválido | `PagamentoRecusado` |

Com `binary_mode: true`, o Checkout Pro só conclui pagamentos aprovados ou recusados. Com `PAGAMENTO_RECUSA_ENCERRA=true` (padrão), a primeira recusa encerra o pagamento; um pagamento aprovado depois disso, para o mesmo orçamento, é estornado automaticamente e o pagamento passa a `ESTORNADO`.

O estorno feito na compensação `EstornarPagamento` aparece no painel da conta de vendedor de teste, na atividade do pagamento.

## 9. Execução local sem Mercado Pago

Com `SPRING_PROFILES_ACTIVE=local` (padrão do `docker-compose.yml`), o adaptador `GatewayPagamentoSimulado` substitui o Mercado Pago:

- a aprovação devolve o link `http://localhost:8081/api/pagamentos/simulacao/{orcamentoId}`;
- a página do link oferece as opções **Aprovar pagamento** e **Recusar pagamento**;
- a escolha percorre o mesmo caminho do webhook (`ProcessarNotificacaoPagamentoUseCase`) e publica `PagamentoAprovado` ou `PagamentoRecusado` na fila `os-eventos` do LocalStack.

O endpoint de simulação existe apenas no perfil `local` e não faz parte do contrato `docs/openapi.json`.
