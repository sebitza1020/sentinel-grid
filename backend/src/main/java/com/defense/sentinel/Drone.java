package com.defense.sentinel;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "drones")
public class Drone extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public UUID id;

    @Column(name = "call_sign", unique = true, nullable = false)
    @NotBlank
    @Size(max = 32)
    public String callSign;

    @Column(nullable = false)
    @NotBlank
    @Size(max = 80)
    public String model;

    @Column(nullable = false)
    @NotBlank
    public String status; // OFFLINE, ACTIVE

    @Column(name = "battery_capacity_mah", nullable = false)
    @NotNull
    @Positive
    public Integer batteryCapacity;

    @Column(name = "model_class", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    @NotNull
    public DroneClass modelClass;

    @Column(name = "top_speed_kmh", nullable = false)
    @NotNull
    @Positive
    public Double topSpeed;

    @Column(name = "radar_range_m", nullable = false)
    @NotNull
    @Positive
    public Double radarRange;

    @Column(name = "payload_capacity_kg", nullable = false)
    @NotNull
    @Positive
    public Double payloadCapacity;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "vision_modes", nullable = false, columnDefinition = "text[]")
    @NotEmpty
    public String[] visionModes;

    @Column(name = "created_at", nullable = false)
    @NotNull
    public LocalDateTime createdAt;

    public Drone() {}

    public static Drone create(DroneCreateRequest request) {
        Drone d = new Drone();
        d.callSign = request.callSign.trim().toUpperCase();
        d.model = request.model.trim();
        d.batteryCapacity = request.batteryCapacity;
        d.modelClass = request.modelClass;
        d.topSpeed = request.topSpeed;
        d.radarRange = request.radarRange;
        d.payloadCapacity = request.payloadCapacity;
        d.visionModes = normalizeVisionModes(request.visionModes);
        d.status = "OFFLINE";
        d.createdAt = LocalDateTime.now();
        return d;
    }

    public void apply(DroneUpdateRequest update) {
        if (update.model != null) model = update.model.trim();
        if (update.status != null) status = update.status.trim().toUpperCase();
        if (update.callSign != null) callSign = update.callSign.trim().toUpperCase();
        if (update.modelClass != null) modelClass = update.modelClass;
        if (update.topSpeed != null) topSpeed = update.topSpeed;
        if (update.radarRange != null) radarRange = update.radarRange;
        if (update.payloadCapacity != null) payloadCapacity = update.payloadCapacity;
        if (update.batteryCapacity != null) batteryCapacity = update.batteryCapacity;
        if (update.visionModes != null) visionModes = normalizeVisionModes(update.visionModes);
    }

    static String[] normalizeVisionModes(String[] modes) {
        return java.util.Arrays.stream(modes)
                .map(String::trim)
                .filter(mode -> !mode.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }
}
