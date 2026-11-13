resource "random_password" "db" {
  length           = 32
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_db_subnet_group" "billing" {
  name       = "${local.identificador_db}-subnet-group"
  subnet_ids = local.private_subnet_ids

  tags = { Name = "${local.identificador_db}-subnet-group" }
}

resource "aws_security_group" "rds" {
  name        = "${local.identificador_db}-sg"
  description = "Postgres do Billing acessivel apenas pelos nos do EKS"
  vpc_id      = local.vpc_id

  tags = { Name = "${local.identificador_db}-sg" }
}

resource "aws_vpc_security_group_ingress_rule" "postgres_do_eks" {
  security_group_id            = aws_security_group.rds.id
  description                  = "Postgres a partir dos nos do EKS"
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
  referenced_security_group_id = data.aws_ssm_parameter.eks_node_security_group_id.value
}

resource "aws_vpc_security_group_egress_rule" "todo_trafego" {
  security_group_id = aws_security_group.rds.id
  description       = "Saida liberada"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_db_parameter_group" "billing" {
  name        = "${local.identificador_db}-pg16"
  family      = "postgres16"
  description = "Ajustes de observabilidade e TLS obrigatorio do PostgreSQL do Billing"

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "billing" {
  identifier     = local.identificador_db
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.billing.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.billing.name

  publicly_accessible                 = false
  iam_database_authentication_enabled = true
  copy_tags_to_snapshot               = true

  multi_az                = false
  skip_final_snapshot     = true
  deletion_protection     = false
  backup_retention_period = 1

  auto_minor_version_upgrade = true
  apply_immediately          = true
}
