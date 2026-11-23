package dev.evraktakip;

import static org.assertj.core.api.Assertions.assertThat;

import dev.evraktakip.kural.TcKimlik;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TcKimlikTest {
    @Test
    void gecerliNumaraKabulEdilir() {
        assertThat(TcKimlik.gecerliMi("10000000146")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345678901", "10000000147", "00000000000", "1000000014", "100000001466", "1000000014a"})
    void gecersizNumaralarReddedilir(String no) {
        assertThat(TcKimlik.gecerliMi(no)).isFalse();
    }

    @Test
    void maskelemeOrtaHaneleriGizler() {
        assertThat(TcKimlik.maskele("10000000146")).isEqualTo("100******46");
    }
}
