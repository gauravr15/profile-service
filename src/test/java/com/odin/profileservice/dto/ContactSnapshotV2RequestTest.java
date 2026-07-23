package com.odin.profileservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactSnapshotV2RequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void decodesVersionedSnakeCaseContract() throws Exception {
        ContactSnapshotV2Request value = mapper.readValue(
                "{\"snapshot_id\":\"550e8400-e29b-41d4-a716-446655440000\","
                        + "\"base_revision\":0,\"country_code\":\"IN\","
                        + "\"contacts\":[{\"phone_number\":\"+919876543210\"}]}",
                ContactSnapshotV2Request.class);

        assertThat(value.getSnapshotId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(value.getBaseRevision()).isZero();
        assertThat(value.getContacts()).singleElement()
                .extracting(ContactSnapshotV2Request.ContactItem::getPhoneNumber)
                .isEqualTo("+919876543210");
    }

    @Test
    void rejectsUnknownTopLevelAndContactFields() {
        assertThatThrownBy(() -> mapper.readValue(
                "{\"snapshot_id\":\"550e8400-e29b-41d4-a716-446655440000\","
                        + "\"base_revision\":0,\"country_code\":\"IN\",\"contacts\":[],\"extra\":true}",
                ContactSnapshotV2Request.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> mapper.readValue(
                "{\"snapshot_id\":\"550e8400-e29b-41d4-a716-446655440000\","
                        + "\"base_revision\":0,\"country_code\":\"IN\","
                        + "\"contacts\":[{\"phone_number\":\"+919876543210\",\"name\":\"private\"}]}",
                ContactSnapshotV2Request.class))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
}
