package com.defense.sentinel;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "drones")
public class Drone extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public UUID id;

    @Column(name = "call_sign", unique = true, nullable = false)
    public String callSign;

    public String model;

    public String status; // OFFLINE, ACTIVE

    @Column(name = "battery_capacity_mah")
    public Integer batteryCapacity;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    public Drone() {}
    
    public static Drone create(String callSign, String model, Integer battery) {
        Drone d = new Drone();
        d.callSign = callSign;
        d.model = model;
        d.batteryCapacity = battery;
        d.status = "OFFLINE";
        d.createdAt = LocalDateTime.now();
        return d;
    }
}