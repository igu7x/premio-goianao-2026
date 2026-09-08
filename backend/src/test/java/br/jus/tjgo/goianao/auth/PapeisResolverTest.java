package br.jus.tjgo.goianao.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.jus.tjgo.goianao.seguranca.Papel;
import br.jus.tjgo.goianao.usuario.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Resolucao de papeis (001/CA-1 a CA-4)")
class PapeisResolverTest {

    private static final String CPF = "10120230100";

    @Mock private AdministradorRepository administradores;
    @Mock private MagistradoLookup magistradoLookup;
    @Mock private UsuarioRepository usuarios;

    private PapeisResolver resolver;

    @BeforeEach
    void preparar() {
        // Sem cadastro de usuario para o CPF: estes casos exercitam as fontes
        // anteriores (tabela de administradores e reconhecimento de magistrado).
        when(usuarios.findByCpf(CPF)).thenReturn(Optional.empty());
        resolver = new PapeisResolver(administradores, magistradoLookup, usuarios);
    }

    @Test
    @DisplayName("CA-1: CPF cadastrado como administrador recebe ADMINISTRADOR")
    void administrador() {
        when(administradores.existsByCpf(CPF)).thenReturn(true);
        when(magistradoLookup.ehMagistradoReconhecido(CPF)).thenReturn(false);

        assertThat(resolver.resolver(CPF)).containsExactly(Papel.ADMINISTRADOR);
    }

    @Test
    @DisplayName("CA-2: CPF que consta como reconhecido recebe MAGISTRADO")
    void magistrado() {
        when(administradores.existsByCpf(CPF)).thenReturn(false);
        when(magistradoLookup.ehMagistradoReconhecido(CPF)).thenReturn(true);

        assertThat(resolver.resolver(CPF)).containsExactly(Papel.MAGISTRADO);
    }

    @Test
    @DisplayName("CA-3: quem nao e admin nem magistrado e SERVIDOR")
    void servidorPorPadrao() {
        when(administradores.existsByCpf(CPF)).thenReturn(false);
        when(magistradoLookup.ehMagistradoReconhecido(CPF)).thenReturn(false);

        assertThat(resolver.resolver(CPF)).containsExactly(Papel.SERVIDOR);
    }

    @Test
    @DisplayName("CA-4: papeis acumulam — admin que tambem e magistrado recebe os dois")
    void acumulaPapeis() {
        when(administradores.existsByCpf(CPF)).thenReturn(true);
        when(magistradoLookup.ehMagistradoReconhecido(CPF)).thenReturn(true);

        // Uniao das capacidades, sem "atuar como" (001/RF-3).
        assertThat(resolver.resolver(CPF))
                .containsExactlyInAnyOrder(Papel.ADMINISTRADOR, Papel.MAGISTRADO);
    }
}
