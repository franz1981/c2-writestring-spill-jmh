package bench.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Person {
      private String firstName;

      @JsonProperty("familyName")
      private String lastName;

      private int age;

      private Address address;

      private Car car;

      public Person() {
      }

      public Person(String firstName, String lastName, int age, Address address) {
          this.firstName = firstName;
          this.lastName = lastName;
          this.age = age;
          this.address = address;
      }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Address getAddress() {
        return address;
    }

    public Car getCar() {
        return car;
    }

    public int getAge() {
        return age;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public void setCar(Car car) {
        this.car = car;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
