package br.jus.tjgo.goianao.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministradorRepository extends JpaRepository<Administrador, String> {

    boolean existsByCpf(String cpf);
}
