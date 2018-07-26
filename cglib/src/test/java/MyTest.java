import java.util.ArrayList;
import net.sf.cglib.PersonDto;
import net.sf.cglib.beans.BeanCopier;
import org.junit.Test;

public class MyTest {

  @Test
  public void test() {
    BeanCopier b = BeanCopier.create(Person7.class, PersonDto.class, false);
    long start = System.currentTimeMillis();
    for (int i = 0; i < 100; i++) {
      Person7 p = getPerson();
      PersonDto dto = new PersonDto();
      b.copy(p, dto, null);
      //System.out.println(dto);
    }
  }


  public Person7 getPerson() {
    Person7 p = new Person7();
    p.setFirstName("neo");
    p.setLastName("jason");
    ArrayList<String> jobTitles = new ArrayList<String>();
    jobTitles.add("1");
    jobTitles.add("2");
    jobTitles.add("3");
    p.setJobTitles(jobTitles);
    p.setSalary(1000L);
    return p;
  }
}
