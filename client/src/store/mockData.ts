import { Node, Edge } from '@xyflow/react';
import { FileItem, CodeIssue, ComplexityMetrics, TestCaseResult } from '../types/studio';

export const INITIAL_JAVA_CODE = `package com.example;

import java.util.*;
import com.example.model.Order;

public class Main {

    // Обработка заказов с вложенной валидацией и подсчетом
    public List<Order> processOrders(List<Order> orders) {
        List<Order> result = new ArrayList<>();
        
        // Быстрая проверка на пустые данные
        if (orders == null || orders.isEmpty()) {
            return result;
        }

        List<Order> processed = new ArrayList<>();
        for (Order order : orders) {
            if (Validator.isValid(order)) {
                // Вызов обработки
                Order p = OrderService.processOrder(order);
                processed.add(p);
            }
        }

        // Повторная проверка валидности (дублирование логики)
        if (Validator.isValid(orders.get(0))) {
            result.addAll(processed);
        }

        // Вложенный поиск O(n²) по идентификаторам
        for (Order a : orders) {
            for (Order b : processed) {
                if (a.getId().equals(b.getId())) {
                    result.add(b);
                }
            }
        }

        return result;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        List<Order> orders = new ArrayList<>();
        int size = scanner.hasNextInt() ? scanner.nextInt() : 0;
        if (scanner.hasNextLine()) scanner.nextLine();
        for (int i = 0; i < size; i++) {
            String line = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            String[] fields = line.split("\\\\s+");
            String id = line.isEmpty() ? Integer.toString(i) : fields[0];
            double total = fields.length > 1 ? Double.parseDouble(fields[fields.length - 1]) : 0;
            orders.add(new Order(id, total));
        }
        List<Order> result = new Main().processOrders(orders);
        System.out.println(result.size());
        for (Order order : result) System.out.println(order.getId() + ":" + order.total());
    }
}
`;

export const OPTIMIZED_JAVA_CODE = `package com.example;

import java.util.*;
import java.util.stream.Collectors;
import com.example.model.Order;

public class Main {

    // Оптимизированный метод O(n) с сохранением идентичного поведения
    public List<Order> processOrders(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyList();
        }

        // Обработка с предикатом
        List<Order> processed = orders.stream()
            .filter(Validator::isValid)
            .map(OrderService::processOrder)
            .collect(Collectors.toList());

        // Оптимизация O(n²) -> O(n) через предвыборку в HashMap
        Map<String, Order> processedById = processed.stream()
            .collect(Collectors.toMap(Order::getId, o -> o, (e1, e2) -> e1));

        List<Order> result = new ArrayList<>();
        if (!orders.isEmpty() && Validator.isValid(orders.get(0))) {
            result.addAll(processed);
        }

        for (Order a : orders) {
            Order b = processedById.get(a.getId());
            if (b != null) {
                result.add(b);
            }
        }

        return result;
    }

    public static void main(String[] args) {
        System.out.println(new Main().processOrders(new ArrayList<>()).size());
    }
}
`;

export const INITIAL_FILES: FileItem[] = [
  {
    id: 'src',
    name: 'src',
    path: 'src',
    type: 'directory',
    children: [
      {
        id: 'pkg-com-example',
        name: 'com.example',
        path: 'src/com.example',
        type: 'directory',
        children: [
          {
            id: 'file-main',
            name: 'Main.java',
            path: 'src/com.example/Main.java',
            type: 'file',
            hasIssues: true,
            code: INITIAL_JAVA_CODE,
          },
          {
            id: 'file-orderservice',
            name: 'OrderService.java',
            path: 'src/com.example/OrderService.java',
            type: 'file',
            hasIssues: false,
            code: `package com.example;\nimport com.example.model.Order;\npublic class OrderService {\n    public static Order processOrder(Order o) {\n        return o;\n    }\n}`,
          },
          {
            id: 'file-validator',
            name: 'Validator.java',
            path: 'src/com.example/Validator.java',
            type: 'file',
            hasIssues: false,
            code: `package com.example;\nimport com.example.model.Order;\npublic class Validator {\n    public static boolean isValid(Order o) {\n        return o != null;\n    }\n}`,
          },
        ],
      },
      {
        id: 'pkg-model',
        name: 'model',
        path: 'src/model',
        type: 'directory',
        children: [
          {
            id: 'file-order',
            name: 'Order.java',
            path: 'src/model/Order.java',
            type: 'file',
            hasIssues: false,
            code: `package com.example.model;\n\npublic record Order(String id, double total) {\n    public String getId() { return id; }\n}`,
          },
          {
            id: 'file-user',
            name: 'User.java',
            path: 'src/model/User.java',
            type: 'file',
            hasIssues: false,
            code: `package com.example.model;\n\npublic record User(String id, String name) {}`,
          },
        ],
      },
      {
        id: 'pkg-util',
        name: 'util',
        path: 'src/util',
        type: 'directory',
        children: [
          {
            id: 'file-constants',
            name: 'Constants.java',
            path: 'src/util/Constants.java',
            type: 'file',
            hasIssues: false,
            code: `package com.example.util;\n\npublic class Constants {\n    public static final int TIMEOUT = 3000;\n}`,
          },
        ],
      },
    ],
  },
];

export const INITIAL_ISSUES: CodeIssue[] = [
  {
    id: 'iss-long-method',
    nodeId: 'node-method-entry',
    title: 'Длинный метод',
    severity: 'HIGH',
    lineStart: 8,
    lineEnd: 42,
    fileName: 'Main.java:12',
    description: 'Метод processOrders содержит 42 строки. Рекомендуется выделить части логики в отдельные классы.',
    risk: 'Ухудшение читаемости и трудность изолированного модульного тестирования.',
    recommendation: 'Разделить монолит на Parser, Analyzer и Optimizer.',
  },
  {
    id: 'iss-dup-logic',
    nodeId: 'node-var-result',
    title: 'Дублирование логики',
    severity: 'MEDIUM',
    lineStart: 28,
    lineEnd: 30,
    fileName: 'Main.java:28',
    description: 'Похожая проверка валидности встречается в OrderService.java и Validator.java.',
    risk: 'Рассинхронизация правил валидации при модификации проекта.',
    recommendation: 'Использовать единый вызов Validator.isValid() в общем пайплайне.',
  },
  {
    id: 'iss-nested-complexity',
    nodeId: 'node-loop-nested',
    title: 'Вложенность O(n²)',
    severity: 'MEDIUM',
    lineStart: 31,
    lineEnd: 37,
    fileName: 'Main.java:31',
    description: 'Вложенный цикл может иметь сложность O(n²) при большом количестве входных данных.',
    risk: 'Квадратичный рост числа сравнений при увеличении входных данных.',
    recommendation: 'Рассмотреть индекс по ключу id и проверить сохранение поведения на тестах.',
  },
];

export const INITIAL_METRICS: ComplexityMetrics = {
  current: 'O(n²)',
  predicted: 'O(n log n)',
  speedup: '—',
  cyclomatic: 14,
  linesOfCode: 42,
  methodsCount: 1,
};

export const INITIAL_NODES: Node[] = [
  {
    id: 'node-method-entry',
    type: 'method',
    position: { x: 260, y: 20 },
    data: {
      label: 'Метод',
      code: 'public List<Order> processOrders(List<Order> orders)',
      lineStart: 8,
      lineEnd: 42,
      hasIssue: true,
      issueId: 'iss-long-method',
    },
  },
  {
    id: 'node-var-result',
    type: 'var',
    position: { x: 260, y: 110 },
    data: {
      label: 'Переменная',
      code: 'List<Order> result = new ArrayList<>()',
      lineStart: 9,
      lineEnd: 9,
    },
  },
  {
    id: 'node-cond-empty',
    type: 'condition',
    position: { x: 260, y: 190 },
    data: {
      label: 'Условие',
      condition: 'orders == null || orders.isEmpty()',
      code: 'if (orders == null || orders.isEmpty())',
      lineStart: 12,
      lineEnd: 14,
    },
  },
  {
    id: 'node-ret-empty',
    type: 'return',
    position: { x: 580, y: 190 },
    data: {
      label: 'Возврат',
      code: 'return result',
      lineStart: 13,
      lineEnd: 13,
    },
  },
  {
    id: 'node-loop-outer',
    type: 'loop',
    position: { x: 260, y: 310 },
    data: {
      label: 'Цикл (for each)',
      loopType: 'foreach',
      code: 'for (Order order : orders)',
      lineStart: 17,
      lineEnd: 24,
    },
  },
  {
    id: 'node-call-process',
    type: 'call',
    position: { x: 580, y: 310 },
    data: {
      label: 'Метод (внешний вызов)',
      code: 'Order p = processOrder(order)',
      lineStart: 20,
      lineEnd: 20,
    },
  },
  {
    id: 'node-loop-nested',
    type: 'loop',
    position: { x: 260, y: 440 },
    data: {
      label: 'Цикл O(n²)',
      loopType: 'foreach',
      code: 'for (Order a : orders) -> for (Order b : processed)',
      lineStart: 31,
      lineEnd: 37,
      hasIssue: true,
      issueId: 'iss-nested-complexity',
      complexityBadge: 'O(n²)',
    },
  },
  {
    id: 'node-ret-final',
    type: 'return',
    position: { x: 260, y: 550 },
    data: {
      label: 'Возврат',
      code: 'return result',
      lineStart: 39,
      lineEnd: 39,
    },
  },
];

export const INITIAL_EDGES: Edge[] = [
  { id: 'e1', source: 'node-method-entry', target: 'node-var-result', type: 'smoothstep' },
  { id: 'e2', source: 'node-var-result', target: 'node-cond-empty', type: 'smoothstep' },
  {
    id: 'e3',
    source: 'node-cond-empty',
    sourceHandle: 'true',
    target: 'node-ret-empty',
    type: 'smoothstep',
    label: 'Да',
    animated: true,
    style: { stroke: '#10b981', strokeWidth: 2 },
  },
  {
    id: 'e4',
    source: 'node-cond-empty',
    sourceHandle: 'false',
    target: 'node-loop-outer',
    type: 'smoothstep',
    label: 'Нет',
    style: { stroke: '#ef4444', strokeWidth: 2 },
  },
  {
    id: 'e5',
    source: 'node-loop-outer',
    target: 'node-call-process',
    type: 'smoothstep',
    label: 'Тело',
  },
  {
    id: 'e6',
    source: 'node-loop-outer',
    target: 'node-loop-nested',
    type: 'smoothstep',
  },
  {
    id: 'e7',
    source: 'node-loop-nested',
    target: 'node-ret-final',
    type: 'smoothstep',
  },
];

export const INITIAL_TESTS: TestCaseResult[] = [
  { id: 't1', name: 'testEmptyList (N=0)', stdin: '0\n', status: 'PENDING', durationMs: 0 },
  { id: 't2', name: 'testSingleOrder (N=1)', stdin: '1\n7 Ольга 10.50\n', status: 'PENDING', durationMs: 0 },
  { id: 't3', name: 'testDuplicates (N=4)', stdin: '4\n1 Иван 10\n1 Иван 10\n2 Анна 5\n2 Анна 5\n', status: 'PENDING', durationMs: 0 },
  { id: 't4', name: 'testLargeDataset (N=1000)', stdin: '1000\n', status: 'PENDING', durationMs: 0 },
  { id: 't5', name: 'testEmptyInput', stdin: '', status: 'PENDING', durationMs: 0 },
  { id: 't6', name: 'testDuplicateIds', stdin: '3\n100 Иван 50\n100 Иван 50\n101 Ольга 30\n', status: 'PENDING', durationMs: 0 },
  { id: 't7', name: 'testZeroAmount', stdin: '1\n5 Промо 0.0\n', status: 'PENDING', durationMs: 0 },
  { id: 't8', name: 'testCyrillicStrings', stdin: '2\n1 Алексей 12.5\n2 Екатерина 88.0\n', status: 'PENDING', durationMs: 0 },
  { id: 't9', name: 'testBoundaryValues', stdin: '2\n1 Мин 0.01\n2 Макс 999999.99\n', status: 'PENDING', durationMs: 0 },
  { id: 't10', name: 'testSortingIntegrity', stdin: '3\n3 Борис 15\n1 Андрей 10\n2 Влад 25\n', status: 'PENDING', durationMs: 0 },
  { id: 't11', name: 'testGeneratedBatch (N=500)', stdin: '500\n', status: 'PENDING', durationMs: 0 },
  { id: 't12', name: 'testRegressionMatch', stdin: '4\n1 A 10\n2 B 20\n3 C 30\n4 D 40\n', status: 'PENDING', durationMs: 0 },
];
