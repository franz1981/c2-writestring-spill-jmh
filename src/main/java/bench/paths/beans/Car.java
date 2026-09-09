package bench.paths.beans;

/** Copy of the {@code Car} nested in the application's {@code ExtendedPerson}. */
public class Car {

    private String brand;
    private String model;

    public Car() {}

    public Car(String brand, String model) {
        this.brand = brand;
        this.model = model;
    }

    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public void setBrand(String brand) { this.brand = brand; }
    public void setModel(String model) { this.model = model; }
}
