package br.jus.tjgo.goianao.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
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

    private static final String EMAIL = "ana.rebelo@tjgo.example";

    @Mock private AdministradorRepository administradores;
    @Mock private MagistradoLookup magistradoLookup;
    @Mock private UsuarioRepository usuarios;

    private PapeisResolver resolver;

    @BeforeEach
    void preparar() {
        // Sem cadastro de usuario para o e-mail: estes casos exercitam as fontes
        // anteriores (tabela de administradores e reconhecimento de magistrado).
        lenient().when(usuarios.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
        resolver = new PapeisResolver(administradores, magistradoLookup, usuarios);
    }

    @Test
    @DisplayName("CA-1: e-mail cadastrado como administrador recebe ADMINISTRADOR")
    void administrador() {
        when(administradores.existsByEmail(EMAIL)).thenReturn(true);
        when(magistradoLookup.ehMagistradoReconhecido(EMAIL)).thenReturn(false);

        assertThat(resolver.resolver(EMAIL)).containsExactly(Papel.ADMINISTRADOR);
    }

    @Test
    @DisplayName("CA-2: e-mail que consta como reconhecido recebe MAGISTRADO")
    void magistrado() {
        when(administradores.existsByEmail(EMAIL)).thenReturn(false);
        when(magistradoLookup.ehMagistradoReconhecido(EMAIL)).thenReturn(true);

        assertThat(resolver.resolver(EMAIL)).containsExactly(Papel.MAGISTRADO);
    }

    @Test
    @DisplayName("CA-3: quem nao e admin nem magistrado e SERVIDOR")
    void servidorPorPadrao() {
        when(administradores.existsByEmail(EMAIL)).thenReturn(false);
        when(magistradoLookup.ehMagistradoReconhecido(EMAIL)).thenReturn(false);

        assertThat(resolver.resolver(EMAIL)).containsExactly(Papel.SERVIDOR);
    }

    @Test
    @DisplayName("CA-4: papeis acumulam — admin que tambem e magistrado recebe os dois")
    void acumulaPapeis() {
        when(administradores.existsByEmail(EMAIL)).thenReturn(true);
        when(magistradoLookup.ehMagistradoReconhecido(EMAIL)).thenReturn(true);

        // Uniao das capacidades, sem "atuar como" (001/RF-3).
        assertThat(resolver.resolver(EMAIL))
                .containsExactlyInAnyOrder(Papel.ADMINISTRADOR, Papel.MAGISTRADO);
    }

    @Test
    @DisplayName("o e-mail e normalizado antes da consulta: maiusculas e espacos nao mudam a pessoa")
    void normalizaAntesDeConsultar() {
        when(administradores.existsByEmail(EMAIL)).thenReturn(true);
        when(magistradoLookup.ehMagistradoReconhecido(EMAIL)).thenReturn(false);

        assertThat(resolver.resolver("  Ana.Rebelo@TJGO.example ")).containsExactly(Papel.ADMINISTRADOR);
    }

    @Test
    @DisplayName("sem e-mail, so o papel padrao")
    void semEmail() {
        assertThat(resolver.resolver(null)).containsExactly(Papel.SERVIDOR);
        assertThat(resolver.resolver("   ")).containsExactly(Papel.SERVIDOR);
    }
}
