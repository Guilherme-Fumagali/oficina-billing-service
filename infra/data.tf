data "aws_caller_identity" "atual" {}

data "aws_region" "atual" {}

data "aws_ssm_parameter" "vpc_id" {
  name = "/oficina/${var.ambiente}/vpc-id"
}

data "aws_ssm_parameter" "private_subnet_ids" {
  name = "/oficina/${var.ambiente}/private-subnet-ids"
}

data "aws_ssm_parameter" "eks_node_security_group_id" {
  name = "/oficina/${var.ambiente}/eks-node-security-group-id"
}

data "aws_ssm_parameter" "eks_cluster_name" {
  name = "/oficina/${var.ambiente}/eks-cluster-name"
}

locals {
  conta              = data.aws_caller_identity.atual.account_id
  regiao             = data.aws_region.atual.name
  vpc_id             = data.aws_ssm_parameter.vpc_id.value
  private_subnet_ids = split(",", data.aws_ssm_parameter.private_subnet_ids.value)
  identificador_db   = "oficina-billing-db-${var.ambiente}"
  fila_comandos      = "oficina-${var.ambiente}-billing-comandos"
  fila_os_eventos    = "arn:aws:sqs:${local.regiao}:${local.conta}:oficina-${var.ambiente}-os-eventos"
  prefixo_ssm        = "/oficina/${var.ambiente}/billing"
}
