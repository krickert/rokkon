datacenter = "dc1"
data_dir = "/consul/data"
log_level = "INFO"
node_name = "consul-dev"
server = true
bootstrap_expect = 1

ui_config {
  enabled = true
}

connect {
  enabled = true
}

ports {
  grpc = 8502
}

client_addr = "0.0.0.0"
