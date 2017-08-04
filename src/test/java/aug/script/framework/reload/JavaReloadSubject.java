package aug.script.framework.reload;

import aug.script.framework.ReloadData;

import java.util.ArrayList;
import java.util.List;

public class JavaReloadSubject {

    private static class IntConverter extends Converter<Integer> {

        IntConverter() {
            super( scala.reflect.ClassTag$.MODULE$.apply(Integer.class));
        }

        @Override
        public String convertToString(Integer integer) {
            return integer.toString();
        }

        @Override
        public Integer convertToValue(String string) {
            return new Integer(string);
        }
    }

    @Reload private static ArrayList<String> strings = new ArrayList<>();

    @Reload private static long longValue = 0;

    @Reload private static int intValue = 0;

    public static void test() {
        ReloadData rl = new ReloadData();
        List<Class<?>> list = new ArrayList<>();
        list.add(JavaReloadSubject.class);

        List<Converter<?>> converters = new ArrayList<>();
        converters.add(new IntConverter());

        longValue = 10;
        intValue = 13;
        strings.add("hello");

        assert(Reloader.saveStaticFields(rl, list, converters).isEmpty());

        strings.clear();
        longValue = 0;
        intValue = 0;

        assert(Reloader.loadStaticFields(rl, list, converters).isEmpty());
        assert(longValue == 10);
        assert(intValue == 13);
        assert(strings.size() == 1);
        assert(strings.get(0).equals("hello"));
    }
}
