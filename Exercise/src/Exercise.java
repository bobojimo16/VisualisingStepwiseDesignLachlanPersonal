

//The answer must have balanced parentheses
//and not contains 'static'
class A{A(String name){this.name=name;}String name;}

class B extends A{



    public B() {
        super(String.valueOf(Math.random()));
    }
}

public class Exercise{

    public static void main(String [] arg){
        assert (!new B().name.equals(new B().name));
    }
}
