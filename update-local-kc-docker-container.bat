@REM copy the newest jar
copy /Y target\keycloak-patreon-provider-1.0-SNAPSHOT.jar E:\_d_a_t_e_n\programmieren\_repos\amcoustics\docker_deploy\keycloak-files\keycloak-patreon-provider-1.0-SNAPSHOT.jar

@REM docker compose down
docker-compose -f E:\_d_a_t_e_n\programmieren\_repos\amcoustics\docker_deploy\docker-compose.yml down

@REM delete the old keycloak container
docker container rm keycloak
docker image rm docker_deploy_keycloak

@REM docker compose up again
docker-compose -f E:\_d_a_t_e_n\programmieren\_repos\amcoustics\docker_deploy\docker-compose.yml up -d
