import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Independent CLI fixture for the four mandatory Java scope sections and bonuses. */
public class Main {
    enum Kind { NEGATIVE, ZERO, POSITIVE }

    interface Named {
        String name();
    }

    abstract static class Metric implements Named {
        protected final String label;

        Metric(String label) {
            this.label = label;
        }

        public String name() {
            return label;
        }

        abstract int apply(int value);
    }

    static class AbsoluteMetric extends Metric {
        AbsoluteMetric() {
            super("absolute");
        }

        int apply(int value) {
            return Math.abs(value);
        }
    }

    record Entry(int value, Kind kind) {
        Entry {
            Objects.requireNonNull(kind);
        }
    }

    static class InputFailure extends IOException {
        InputFailure(String message) {
            super(message);
        }
    }

    static final Map<String, Integer> SCOPE_LIMITS = new TreeMap<>();

    static {
        SCOPE_LIMITS.put("min", -1000);
        SCOPE_LIMITS.put("max", 1000);
    }

    static <T extends Comparable<T>> Optional<T> maximum(Collection<T> values) {
        return values.stream().max(Comparator.naturalOrder());
    }

    static List<Integer> readNumbers(Scanner scanner) throws InputFailure {
        List<Integer> numbers = new LinkedList<>();
        while (scanner.hasNext()) {
            String token = scanner.next();
            try {
                Integer number = Integer.valueOf(token);
                if (number < SCOPE_LIMITS.get("min") || number > SCOPE_LIMITS.get("max")) {
                    throw new InputFailure("range:" + token);
                }
                numbers.add(number);
            } catch (NumberFormatException error) {
                throw new InputFailure("integer:" + token);
            }
        }
        return numbers;
    }

    static List<Integer> uniqueNumbers(List<Integer> values) {
        List<Integer> unique = new ArrayList<>();
        for (Integer value : values) {
            if (!unique.contains(value)) {
                unique.add(value);
            }
        }
        return unique;
    }

    static long allowedCount(List<Integer> values, List<Integer> allowed) {
        return values.stream().filter(value -> allowed.contains(value)).count();
    }

    static String scopeReport(List<Integer> numbers) {
        LinkedList<Integer> queue = new LinkedList<>(numbers);
        Iterator<Integer> iterator = queue.iterator();
        int sum = 0;
        while (iterator.hasNext()) {
            int value = iterator.next();
            if (value == 0) {
                iterator.remove();
                continue;
            }
            sum += value;
        }
        int rounds = 0;
        do {
            rounds++;
        } while (rounds < queue.size());

        Object candidate = new AbsoluteMetric();
        int absolute = 0;
        if (candidate instanceof Metric metric) {
            for (Integer value : queue) {
                absolute += metric.apply(value);
            }
        }
        Set<Integer> sorted = new TreeSet<>(queue);
        Set<Integer> hashed = new HashSet<>(queue);
        List<Entry> entries = new ArrayList<>();
        for (int value : numbers) {
            Kind kind = value < 0 ? Kind.NEGATIVE : value == 0 ? Kind.ZERO : Kind.POSITIVE;
            entries.add(new Entry(value, kind));
        }
        Predicate<Entry> positive = e -> e.kind() == Kind.POSITIVE;
        Map<Kind, Long> counts = entries.stream().collect(Collectors.groupingBy(Entry::kind,
                () -> new EnumMap<>(Kind.class), Collectors.counting()));
        Integer[] array = queue.toArray(Integer[]::new);
        Arrays.sort(array, Comparator.reverseOrder());
        List<Integer> copied = new ArrayList<>(List.of(1, 2, 3));
        Collections.reverse(copied);
        Map<String, String> dictionary = Map.of("name", "Числа");
        Iterable<Integer> iterable = sorted;
        int visited = 0;
        for (Integer ignored : iterable) {
            visited++;
        }
        String category;
        switch (numbers.size()) {
            case 0: category = "empty"; break;
            case 1: category = "single"; break;
            default: category = "many";
        }
        try (Formatter formatter = new Formatter(Locale.ROOT)) {
            formatter.format("%s %s sum=%d abs=%d rounds=%d max=%s%n", dictionary.get("name"),
                    category, sum, absolute, rounds, maximum(queue).map(Object::toString).orElse("none"));
            formatter.format("sorted=%s unique=%s kinds=%s positives=%d%n", sorted,
                    uniqueNumbers(numbers), counts, entries.stream().filter(positive).count());
            formatter.format("allowed=%d hash=%d visited=%d reversed=%s array=%s%n",
                    allowedCount(numbers, List.of(-1, 0, 1)), hashed.size(), visited, copied, Arrays.toString(array));
            return formatter.toString();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        try {
            List<Integer> numbers = readNumbers(scanner);
            System.out.print(scopeReport(numbers));
        } catch (InputFailure error) {
            System.err.println(String.format(Locale.ROOT, "Ошибка: %s", error.getMessage()));
        } finally {
            System.out.println("Готово");
        }
    }
}
