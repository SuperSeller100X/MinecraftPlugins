package org.bukkit.util;
public class Vector implements Cloneable {
    private double x, y, z;
    public Vector(double x, double y, double z) { this.x=x; this.y=y; this.z=z; }
    public Vector subtract(Vector other) { x-=other.x; y-=other.y; z-=other.z; return this; }
    public Vector normalize() { double length=length(); if(length>0) { x/=length; y/=length; z/=length; } return this; }
    public Vector multiply(double m) { x*=m; y*=m; z*=m; return this; }
    public Vector add(Vector other) { x+=other.x; y+=other.y; z+=other.z; return this; }
    public Vector setY(double value) { y=value; return this; }
    public double length() { return Math.sqrt(lengthSquared()); }
    public double dot(Vector other) { return x*other.x+y*other.y+z*other.z; }
    public double lengthSquared() { return dot(this); }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    @Override public Vector clone() { return new Vector(x,y,z); }
}
