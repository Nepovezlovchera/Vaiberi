import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/**
 * Демо-монолит для Java Blueprints: обработка заказов интернет-магазина.
 * Ввод (stdin):
 *   N                      — число заказов
 *   N строк                — id клиент товар количество цена
 *   M                      — число промокодов
 *   M строк                — КОД процент
 *   K                      — число клиентов в чёрном списке
 *   K строк                — имя клиента
 * Намеренные проблемы для анализатора:
 *   - uniqueCustomers, uniqueProducts: list.contains в цикле (O(n²));
 *   - findDuplicateIds, countSharedProducts: вложенные циклы с поиском по ключу (O(n²));
 *   - countBlacklisted: contains по неизменяемому списку в цикле;
 *   - printReport: длинный метод с большой цикломатической сложностью;
 *   - повторяющиеся проверки orders == null || orders.isEmpty().
 */
public class Main {

    // ======================================================================
    // Модель данных
    // ======================================================================

    static class Order {
        final String id;
        final String customer;
        final String product;
        final int quantity;
        final double price;

        Order(String id, String customer, String product, int quantity, double price) {
            this.id = id;
            this.customer = customer;
            this.product = product;
            this.quantity = quantity;
            this.price = price;
        }

        double total() {
            return quantity * price;
        }

        @Override
        public String toString() {
            return id + "(" + customer + ", " + product + " x" + quantity + ")";
        }
    }

    static class PromoCode {
        final String code;
        final int percent;

        PromoCode(String code, int percent) {
            this.code = code;
            this.percent = percent;
        }
    }

    static class InvalidOrderException extends Exception {
        InvalidOrderException(String message) {
            super(message);
        }
    }

    static class ValidationException extends RuntimeException {
        ValidationException(String message) {
            super(message);
        }
    }

    static class Stats {
        final int count;
        final double sum;
        final double min;
        final double max;

        Stats(int count, double sum, double min, double max) {
            this.count = count;
            this.sum = sum;
            this.min = min;
            this.max = max;
        }

        double average() {
            return count == 0 ? 0 : sum / count;
        }

        static Stats of(List<Order> orders) {
            if (orders == null || orders.isEmpty()) {
                return new Stats(0, 0, 0, 0);
            }
            double sum = 0;
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (Order o : orders) {
                double t = o.total();
                sum += t;
                if (t < min) {
                    min = t;
                }
                if (t > max) {
                    max = t;
                }
            }
            return new Stats(orders.size(), sum, min, max);
        }
    }

    // ======================================================================
    // Разбор ввода
    // ======================================================================

    static Order parseOrder(String line) throws InvalidOrderException {
        if (line == null || line.trim().isEmpty()) {
            throw new InvalidOrderException("Пустая строка заказа");
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 5) {
            throw new InvalidOrderException("Неверное число полей (" + parts.length + "): " + line);
        }
        try {
            int qty = Integer.parseInt(parts[3]);
            double price = Double.parseDouble(parts[4]);
            if (qty <= 0) {
                throw new InvalidOrderException("Количество должно быть положительным: " + line);
            }
            if (price < 0) {
                throw new InvalidOrderException("Цена не может быть отрицательной: " + line);
            }
            return new Order(parts[0], parts[1], parts[2], qty, price);
        } catch (NumberFormatException e) {
            throw new InvalidOrderException("Не число в строке: " + line);
        }
    }

    static PromoCode parsePromo(String line) throws InvalidOrderException {
        String[] parts = line.trim().split("\\s+");
        if (parts.length != 2) {
            throw new InvalidOrderException("Неверный промокод: " + line);
        }
        try {
            int percent = Integer.parseInt(parts[1]);
            if (percent < 0 || percent > 90) {
                throw new InvalidOrderException("Скидка вне диапазона 0..90: " + line);
            }
            return new PromoCode(parts[0], percent);
        } catch (NumberFormatException e) {
            throw new InvalidOrderException("Скидка не число: " + line);
        }
    }

    static int readCount(Scanner sc) {
        if (!sc.hasNextLine()) {
            return 0;
        }
        String line = sc.nextLine().trim();
        if (line.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(line);
        } catch (NumberFormatException e) {
            System.err.println("Ожидалось число, получено: " + line);
            return 0;
        }
    }

    static List<Order> readOrders(Scanner sc) {
        List<Order> orders = new ArrayList<>();
        int n = readCount(sc);
        for (int i = 0; i < n && sc.hasNextLine(); i++) {
            String line = sc.nextLine();
            try {
                orders.add(parseOrder(line));
            } catch (InvalidOrderException e) {
                System.err.println("Пропущено: " + e.getMessage());
            }
        }
        return orders;
    }

    static List<PromoCode> readPromos(Scanner sc) {
        List<PromoCode> promos = new ArrayList<>();
        int m = readCount(sc);
        for (int i = 0; i < m && sc.hasNextLine(); i++) {
            String line = sc.nextLine();
            try {
                promos.add(parsePromo(line));
            } catch (InvalidOrderException e) {
                System.err.println("Пропущено: " + e.getMessage());
            }
        }
        return promos;
    }

    static List<String> readBlacklist(Scanner sc) {
        List<String> names = new ArrayList<>();
        int k = readCount(sc);
        for (int i = 0; i < k && sc.hasNextLine(); i++) {
            String name = sc.nextLine().trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    // ======================================================================
    // Анализ данных
    // ======================================================================

    /** O(n²): contains по ArrayList внутри цикла. */
    static List<String> uniqueCustomers(List<Order> orders) {
        List<String> result = new ArrayList<>();
        if (orders == null || orders.isEmpty()) {
            return result;
        }
        for (Order o : orders) {
            if (!result.contains(o.customer)) {
                result.add(o.customer);
            }
        }
        Collections.sort(result);
        return result;
    }

    /** O(n²): то же для товаров. */
    static List<String> uniqueProducts(List<Order> orders) {
        List<String> result = new ArrayList<>();
        if (orders == null || orders.isEmpty()) {
            return result;
        }
        for (Order o : orders) {
            if (!result.contains(o.product)) {
                result.add(o.product);
            }
        }
        Collections.sort(result);
        return result;
    }

    /** O(n²): вложенные циклы, поиск пар с одинаковым id. */
    static int findDuplicateIds(List<Order> first, List<Order> second) {
        int count = 0;
        for (Order a : first) {
            for (Order b : second) {
                if (a.id.equals(b.id)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** O(n²): вложенные циклы, совпадение по товару; порядок и повторы пар важны. */
    static double countSharedProducts(List<Order> left, List<Order> right) {
        double acc = 0;
        for (Order a : left) {
            for (Order b : right) {
                if (a.product.equals(b.product)) {
                    acc += Math.min(a.quantity, b.quantity);
                    System.out.println("  пара: " + a.id + " ~ " + b.id);
                }
            }
        }
        return acc;
    }

    /** contains по неизменяемому списку внутри цикла. */
    static int countBlacklisted(List<Order> orders, List<String> blacklist) {
        int count = 0;
        for (Order o : orders) {
            if (blacklist.contains(o.customer)) {
                count++;
            }
        }
        return count;
    }

    static Map<String, Double> revenueByCustomer(List<Order> orders) {
        Map<String, Double> map = new TreeMap<>();
        if (orders == null || orders.isEmpty()) {
            return map;
        }
        for (Order o : orders) {
            map.merge(o.customer, o.total(), Double::sum);
        }
        return map;
    }

    static Map<String, Integer> quantityByProduct(List<Order> orders) {
        Map<String, Integer> map = new TreeMap<>();
        for (Order o : orders) {
            Integer current = map.get(o.product);
            map.put(o.product, current == null ? o.quantity : current + o.quantity);
        }
        return map;
    }

    static String category(Order o) {
        double total = o.total();
        if (total >= 100000) {
            return "премиум";
        } else if (total >= 10000) {
            return "крупный";
        } else if (total >= 1000) {
            return "средний";
        }
        return "мелкий";
    }

    static Map<String, Integer> categories(List<Order> orders) {
        Map<String, Integer> stats = new TreeMap<>();
        for (Order o : orders) {
            stats.merge(category(o), 1, Integer::sum);
        }
        return stats;
    }

    static String quantityLabel(int qty) {
        String label;
        switch (qty) {
            case 1:
                label = "штучный";
                break;
            case 2:
            case 3:
                label = "малый опт";
                break;
            case 4:
            case 5:
                label = "опт";
                break;
            default:
                label = qty > 5 ? "крупный опт" : "неизвестно";
        }
        return label;
    }

    static double applyPromo(double amount, List<PromoCode> promos, String code) {
        if (code == null) {
            return amount;
        }
        for (PromoCode p : promos) {
            if (p.code.equals(code)) {
                return amount * (100 - p.percent) / 100.0;
            }
        }
        return amount;
    }

    static List<Order> topOrders(List<Order> orders, int limit) {
        List<Order> copy = new ArrayList<>(orders);
        Collections.sort(copy, new Comparator<Order>() {
            @Override
            public int compare(Order x, Order y) {
                int c = Double.compare(y.total(), x.total());
                return c != 0 ? c : x.id.compareTo(y.id);
            }
        });
        if (copy.size() > limit) {
            return new ArrayList<>(copy.subList(0, limit));
        }
        return copy;
    }

    static List<Order> removeSmall(List<Order> orders, double threshold) {
        List<Order> copy = new ArrayList<>(orders);
        Iterator<Order> it = copy.iterator();
        while (it.hasNext()) {
            Order o = it.next();
            if (o.total() < threshold) {
                it.remove();
            }
        }
        return copy;
    }

    static void validate(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            throw new ValidationException("Нет данных для проверки");
        }
        Map<String, Integer> ids = new HashMap<>();
        for (Order o : orders) {
            Integer seen = ids.get(o.id);
            if (seen != null) {
                throw new ValidationException("Повторяющийся id: " + o.id);
            }
            ids.put(o.id, 1);
        }
    }

    // ======================================================================
    // Вывод отчёта
    // ======================================================================

    /** Длинный метод с большой цикломатической сложностью: кандидат на декомпозицию. */
    static void printReport(List<Order> orders, List<PromoCode> promos, List<String> blacklist) {
        if (orders == null || orders.isEmpty()) {
            System.out.println("Заказов нет");
            return;
        }
        Stats stats = Stats.of(orders);
        System.out.println("=== Отчёт ===");
        System.out.println("Заказов: " + stats.count);
        System.out.printf("Выручка: %.2f%n", stats.sum);
        System.out.printf("Средний чек: %.2f%n", stats.average());
        System.out.printf("Минимум: %.2f, максимум: %.2f%n", stats.min, stats.max);
        List<String> customers = uniqueCustomers(orders);
        System.out.println("Клиенты (" + customers.size() + "): " + customers);
        List<String> products = uniqueProducts(orders);
        System.out.println("Товары (" + products.size() + "): " + products);
        Map<String, Double> revenue = revenueByCustomer(orders);
        for (Map.Entry<String, Double> e : revenue.entrySet()) {
            String mark = "";
            if (e.getValue() > 100000) {
                mark = " [VIP]";
            } else if (e.getValue() > 10000) {
                mark = " [постоянный]";
            } else if (e.getValue() < 500) {
                mark = " [новичок]";
            }
            System.out.printf("  %s: %.2f%s%n", e.getKey(), e.getValue(), mark);
        }
        Map<String, Integer> qty = quantityByProduct(orders);
        for (Map.Entry<String, Integer> e : qty.entrySet()) {
            System.out.println("  товар " + e.getKey() + ": " + e.getValue() + " шт., " + quantityLabel(e.getValue()));
        }
        Map<String, Integer> cats = categories(orders);
        System.out.println("Категории: " + cats);
        int blocked = countBlacklisted(orders, blacklist);
        if (blocked > 0) {
            System.out.println("Заказов от клиентов из чёрного списка: " + blocked);
        } else {
            System.out.println("Чёрный список: совпадений нет");
        }
        List<Order> top = topOrders(orders, 3);
        int place = 1;
        for (Order o : top) {
            double discounted = applyPromo(o.total(), promos, "SALE");
            if (discounted < o.total()) {
                System.out.printf("%d. %s -> %.2f (со скидкой %.2f)%n", place, o, o.total(), discounted);
            } else {
                System.out.printf("%d. %s -> %.2f%n", place, o, o.total());
            }
            place++;
        }
        List<Order> big = removeSmall(orders, 1000);
        System.out.println("Заказов от 1000: " + big.size());
        try {
            validate(orders);
            System.out.println("Проверка id: ок");
        } catch (ValidationException e) {
            System.out.println("Проверка id: " + e.getMessage());
        } finally {
            System.out.println("Проверка завершена");
        }
    }

    // ======================================================================
    // Точка входа
    // ======================================================================

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in, "UTF-8");
        List<Order> orders = readOrders(sc);
        List<PromoCode> promos = readPromos(sc);
        List<String> blacklist = readBlacklist(sc);

        printReport(orders, promos, blacklist);

        List<Order> half = orders.subList(0, orders.size() / 2);
        List<Order> rest = orders.subList(orders.size() / 2, orders.size());
        System.out.println("Пересечений id между половинами: " + findDuplicateIds(half, rest));
        System.out.println("Общих товаров (штук): " + countSharedProducts(half, rest));
    }
}
