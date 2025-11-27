package com.aisocialgame.model;

public class Persona {
    private String id;
    private String name;
    private String trait;
    private String avatar;

    public Persona() {}

    public Persona(String id, String name, String trait, String avatar) {
        this.id = id;
        this.name = name;
        this.trait = trait;
        this.avatar = avatar;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTrait() { return trait; }
    public String getAvatar() { return avatar; }
}
