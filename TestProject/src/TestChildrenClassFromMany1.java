
public class TestChildrenClassFromMany1 extends TestClassWithManyChildren {
	int test_var=20;
	int test_function(int test_input_1,int test_input_2)
	{
		return test_input_1+test_input_2*10;
	}
	int get_test_var()
	{
		return test_var;
	}
}