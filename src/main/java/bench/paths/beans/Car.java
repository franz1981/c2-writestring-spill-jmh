package bench.paths.beans;

/** Nested bean, as in the end-to-end benchmark. */
public class Car {
    public String brand;
    public String model;

    public Car() {
    }

    public Car(String brand, String model) {
        this.brand = brand;
        this.model = model;
    }
}
