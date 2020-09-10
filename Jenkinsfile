@Library(value="kids-first/aws-infra-jenkins-shared-libraries", changelog=false) _
ecs_service_type_1_standard {
    projectName = "rollcall"
    environments = "dev,qa,prd"
    docker_image_type = "alpine"
    docker_workdir_path = "/srv/rollcall"
    entrypoint_command = "/srv/rollcall/exec/run.sh"
    quick_deploy = "true"
    internal_app = "true"
    external_config_repo = "false"
    container_port = "9001"
    vcpu_container             = "2048"
    memory_container           = "4096"
    vcpu_task                  = "2048"
    memory_task                = "4096"
    health_check_path = "/swagger-ui.html"
    dependencies = "ecr"
    friendly_dns_name = "rollcall"
}
