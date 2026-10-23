import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * Демо-монолит. Вход: N, затем N строк "id клиент сумма".
 * Намеренные дефекты:
 *  - uniqueCustomers: contains по ArrayList в цикле, O(n²), исправляется шаблонным автофиксом;
 *  - findDuplicates: вложенные циклы, O(n²);
 *  - printReport: длинный метод с ветвлениями.
 */
public class Main {

    static class Order {
        final int id;
        final String customer;
        final double amount;

        Order(int id, String customer, double amount) {
            this.id = id;
            this.customer = customer;
            this.amount = amount;
        }
    }

    static class InvalidOrderException extends Exception {
        InvalidOrderException(String message) {
            super(message);
        }
    }

    static class Stats {
        final double min;
        final double max;
        final double avg;

        Stats(double min, double max, double avg) {
            this.min = min;
            this.max = max;
            this.avg = avg;
        }

        static Stats of(List<Order> orders) {
            if (orders.isEmpty()) {
                return new Stats(0, 0, 0);
            }
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            double sum = 0;
            for (Order o : orders) {
                min = Math.min(min, o.amount);
                max = Math.max(max, o.amount);
                sum += o.amount;
            }
            return new Stats(min, max, sum / orders.size());
        }
    }

    static Order parseOrder(String line) throws InvalidOrderException {
        String[] p = line.trim().split("\\s+");
        if (p.length != 3) {
            throw new InvalidOrderException("Неверный формат: " + line);
        }
        try {
            return new Order(Integer.parseInt(p[0]), p[1], Double.parseDouble(p[2]));
        } catch (NumberFormatException e) {
            throw new InvalidOrderException("Не число: " + line);
        }
    }

    /** O(n²): вложенные циклы + contains по списку. */
    static List<Order> findDuplicates(List<Order> orders) {
        List<Order> dups = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                if (orders.get(i).id == orders.get(j).id && !dups.contains(orders.get(j))) {
                    dups.add(orders.get(j));
                }
            }
        }
        return dups;
    }

    /** O(n²): contains на ArrayList внутри цикла. */
    static List<String> uniqueCustomers(List<Order> orders) {
        List<String> result = new ArrayList<>();
        for (Order o : orders) {
            if (!result.contains(o.customer)) {
                result.add(o.customer);
            }
        }
        Collections.sort(result);
        return result;
    }

    static double total(List<Order> orders) {
        double sum = 0;
        for (Order o : orders) {
            sum += o.amount;
        }
        return sum;
    }

    static String category(double amount) {
        switch ((int) (amount / 50)) {
            case 0:
                return "малый";
            case 1:
                return "средний";
            default:
                return "крупный";
        }
    }

    static Map<String, Double> totalsByCustomer(List<Order> orders) {
        Map<String, Double> totals = new TreeMap<>();
        for (Order o : orders) {
            totals.merge(o.customer, o.amount, Double::sum);
        }
        return totals;
    }

    static void printReport(List<Order> orders, int invalid) {
        System.out.printf("Заказов принято: %d, отброшено: %d%n", orders.size(), invalid);
        System.out.printf("Сумма: %.2f%n", total(orders));
        Stats st = Stats.of(orders);
        if (orders.isEmpty()) {
            System.out.println("Статистика: нет данных");
        } else {
            System.out.printf("Мин: %.2f, макс: %.2f, средняя: %.2f%n", st.min, st.max, st.avg);
        }
        System.out.println("Клиенты: " + uniqueCustomers(orders));
        for (Map.Entry<String, Double> e : totalsByCustomer(orders).entrySet()) {
            System.out.printf("  %s: %.2f%n", e.getKey(), e.getValue());
        }
        for (Order o : orders) {
            if (o.amount > 90) {
                System.out.println("Крупный заказ " + o.id + " (" + category(o.amount) + ")");
            } else if (o.amount < 5) {
                System.out.println("Мелкий заказ " + o.id + " (" + category(o.amount) + ")");
            }
        }
        for (Order d : findDuplicates(orders)) {
            System.out.println("Дубликат id=" + d.id + " клиент=" + d.customer);
        }
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
        List<Order> orders = new ArrayList<>();
        int invalid = 0;

        int n = in.hasNextLine() ? Integer.parseInt(in.nextLine().trim()) : 0;
        for (int i = 0; i < n && in.hasNextLine(); i++) {
            try {
                orders.add(parseOrder(in.nextLine()));
            } catch (InvalidOrderException e) {
                invalid++;
                System.err.println("Пропуск: " + e.getMessage());
            }
        }
        try {
            printReport(orders, invalid);
        } finally {
            System.out.println("Готово");
        }
    }
}
