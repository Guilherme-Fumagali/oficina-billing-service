variable "aws_region" {
  description = "Região da AWS."
  type        = string
  default     = "us-east-1"
}

variable "ambiente" {
  description = "Ambiente lógico (staging ou prod)."
  type        = string
  default     = "staging"

  validation {
    condition     = contains(["staging", "prod"], var.ambiente)
    error_message = "ambiente deve ser staging ou prod."
  }
}

variable "db_instance_class" {
  description = "Classe da instância RDS do Billing."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "Armazenamento do RDS em GB."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Nome do banco criado na instância."
  type        = string
  default     = "billing"
}

variable "db_username" {
  description = "Usuário administrador do banco."
  type        = string
  default     = "billing"
}

variable "engine_version" {
  description = "Versão do PostgreSQL."
  type        = string
  default     = "16"
}

variable "mp_access_token" {
  description = "Access token do vendedor de teste do Mercado Pago, vindo do secret MP_ACCESS_TOKEN do repositório."
  type        = string
  sensitive   = true
}

variable "mp_webhook_secret" {
  description = "Assinatura secreta dos webhooks do Mercado Pago, vinda do secret MP_WEBHOOK_SECRET do repositório."
  type        = string
  sensitive   = true
}
