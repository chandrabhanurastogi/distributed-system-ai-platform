package com.distributedplatform.disputeservice.scheduling;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentHasherTest {

    @Test
    void sha256Hex_withSameInput_returnsSameHash() {
        String input = "Cardholder Dispute Resolution Policy";

        assertThat(ContentHasher.sha256Hex(input)).isEqualTo(ContentHasher.sha256Hex(input));
    }

    @Test
    void sha256Hex_withDifferentInput_returnsDifferentHash() {
        String hashA = ContentHasher.sha256Hex("Cardholder Dispute Resolution Policy - v1");
        String hashB = ContentHasher.sha256Hex("Cardholder Dispute Resolution Policy - v2");

        assertThat(hashA).isNotEqualTo(hashB);
    }

    @Test
    void sha256Hex_withKnownTestVector_matchesTheRealSha256OfAbc() {
        // Verified against a real sha256sum, not recalled from memory (Rule 9):
        // printf 'abc' | shasum -a 256
        assertThat(ContentHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
