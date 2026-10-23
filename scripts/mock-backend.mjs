import http from 'node:http';

const PORT = 8080;

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Accept',
};

const server = http.createServer(async (req, res) => {
  // Handle CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, corsHeaders);
    res.end();
    return;
  }

  const url = new URL(req.url, `http://${req.headers.host}`);
  const pathname = url.pathname;

  let body = '';
  req.on('data', chunk => { body += chunk; });
  await new Promise(resolve => req.on('end', resolve));

  let parsedBody = {};
  if (body) {
    try { parsedBody = JSON.parse(body); } catch (_) {}
  }

  // Health
  if (pathname === '/api/health' || (pathname === '/api/analysis' && req.method === 'POST' && (!parsedBody.files || parsedBody.files.length === 0))) {
    res.writeHead(200, { ...corsHeaders, 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'UP', backend: 'Spring Boot 3 Stub / Live Mode' }));
    return;
  }

  // POST /api/analysis
  if (pathname === '/api/analysis' && req.method === 'POST') {
    res.writeHead(200, { ...corsHeaders, 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      issues: [
        {
          id: 'iss-nested-complexity',
          nodeId: 'node-loop-outer',
          type: 'NESTED_LOOP',
          title: 'Квадратичная сложность O(n²)',
          severity: 'HIGH',
          lineStart: 31,
          lineEnd: 37,
          fileName: 'Main.java',
          description: 'Вложенный обход списков без индексирования приводит к деградации производительности на больших объемах данных.',
          risk: 'Превышение тайм-аута при размере коллекции > 10,000 элементов.',
          recommendation: 'Заменить вложенный цикл на предвыборку по ключу в Map (O(1) поиск).',
          complexityBefore: 'O(n²)',
          complexityAfter: 'O(n log n)',
        },
        {
          id: 'iss-long-method',
          nodeId: 'node-method-main',
          type: 'LONG_METHOD',
          title: 'Длинный метод (>30 строк)',
          severity: 'MEDIUM',
          lineStart: 12,
          lineEnd: 55,
          fileName: 'Main.java',
          description: 'Метод processData совмещает парсинг, фильтрацию, сортировку и вывод.',
          risk: 'Сложность сопровождения и тестирования.',
          recommendation: 'Декомпозировать на независимые функциональные классы.',
        }
      ],
      metrics: {
        currentComplexity: 'O(n²)',
        predictedComplexity: 'O(n log n)',
        expectedSpeedup: '5.2x',
        issuesCount: 2,
        cyclomaticComplexity: 7,
        linesOfCode: 85,
        methodCount: 3,
      }
    }));
    return;
  }

  // POST /api/graph
  if (pathname === '/api/graph' && req.method === 'POST') {
    res.writeHead(200, { ...corsHeaders, 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      methods: [
        {
          className: 'Main',
          methodId: 'Main.processData',
          methodSignature: 'public static void processData(List<Order> orders, List<Customer> customers)',
          fileName: 'Main.java',
          lineStart: 12,
          lineEnd: 48,
          nodes: [
            { id: 'node-method-main', type: 'method', data: { label: 'Метод: processData', code: 'public static void processData(...)', lineStart: 12, lineEnd: 48, hasIssue: false } },
            { id: 'node-cond-empty', type: 'condition', data: { label: 'orders == null || orders.isEmpty()', code: 'if (orders == null || orders.isEmpty()) return;', condition: 'orders.isEmpty()', lineStart: 14, lineEnd: 16, hasIssue: false } },
            { id: 'node-ret-empty', type: 'return', data: { label: 'Возврат (пусто)', code: 'return;', lineStart: 15, lineEnd: 15, hasIssue: false } },
            { id: 'node-loop-outer', type: 'loop', data: { label: 'Внешний цикл for: Order order', code: 'for (Order order : orders) { ... }', loopType: 'foreach', lineStart: 31, lineEnd: 37, hasIssue: true, issueId: 'iss-nested-complexity' } },
            { id: 'node-loop-inner', type: 'loop', data: { label: 'Вложенный цикл: Customer customer', code: 'for (Customer c : customers) { ... }', loopType: 'foreach', lineStart: 33, lineEnd: 36, hasIssue: true, issueId: 'iss-nested-complexity' } },
            { id: 'node-ret-done', type: 'return', data: { label: 'Возврат (успешно)', code: 'return result;', lineStart: 46, lineEnd: 46, hasIssue: false } }
          ],
          edges: [
            { id: 'e-1', source: 'node-method-main', target: 'node-cond-empty', type: 'smoothstep' },
            { id: 'e-2', source: 'node-cond-empty', target: 'node-ret-empty', type: 'smoothstep', label: 'Да' },
            { id: 'e-3', source: 'node-cond-empty', target: 'node-loop-outer', type: 'smoothstep', label: 'Нет' },
            { id: 'e-4', source: 'node-loop-outer', target: 'node-loop-inner', type: 'smoothstep', label: 'Тело цикла' },
            { id: 'e-5', source: 'node-loop-outer', target: 'node-ret-done', type: 'smoothstep', label: 'Выход' }
          ]
        }
      ],
      metrics: {
        currentComplexity: 'O(n²)',
        predictedComplexity: 'O(n log n)',
        expectedSpeedup: '5.2x',
        issuesCount: 2,
        cyclomaticComplexity: 7,
        linesOfCode: 85,
        methodCount: 3,
      }
    }));
    return;
  }

  // POST /api/optimize
  if (pathname === '/api/optimize' && req.method === 'POST') {
    res.writeHead(200, { ...corsHeaders, 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      strategy: 'TEMPLATE',
      explanation: 'Вложенный цикл O(n²) заменен на предвыборку заказчиков в Map<Long, Customer> за O(n) с последующим O(1) поиском.',
      files: [
        {
          path: 'Main.java',
          source: `package com.axenix.demo;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("Java Blueprints Optimized Execution");
    }

    // ОПТИМИЗИРОВАНО: O(n log n) за счет индексирования в Map
    public static List<String> processData(List<Order> orders, List<Customer> customers) {
        if (orders == null || orders.isEmpty()) return Collections.emptyList();
        
        // Индексация клиентов в O(M)
        Map<Long, Customer> customerMap = new HashMap<>();
        for (Customer c : customers) {
            customerMap.put(c.getId(), c);
        }

        List<String> results = new ArrayList<>();
        // Линейный проход по заказам в O(N)
        for (Order order : orders) {
            Customer customer = customerMap.get(order.getCustomerId());
            if (customer != null && order.getAmount() > 100) {
                results.add(customer.getName() + " -> " + order.getId());
            }
        }
        return results;
    }
}`
        }
      ]
    }));
    return;
  }

  // POST /api/decompose
  if (pathname === '/api/decompose' && req.method === 'POST') {
    res.writeHead(200, { ...corsHeaders, 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      explanation: 'Монолит успешно декомпозирован на 4 независимых класса: Main, Parser, Analyzer, Optimizer.',
      createdFiles: ['Parser.java', 'Analyzer.java', 'Optimizer.java', 'Main.java'],
      files: [
        {
          path: 'Main.java',
          source: `package com.axenix.demo;\n\npublic class Main {\n    public static void main(String[] args) {\n        Parser parser = new Parser();\n        Analyzer analyzer = new Analyzer();\n        Optimizer optimizer = new Optimizer();\n        System.out.println("Decomposed Architecture Active");\n    }\n}`
        },
        {
          path: 'Parser.java',
          source: `package com.axenix.demo;\n\nimport java.util.*;\n\npublic class Parser {\n    public List<String> parseInput(String raw) {\n        return Arrays.asList(raw.split("\\n"));\n    }\n}`
        },
        {
          path: 'Analyzer.java',
          source: `package com.axenix.demo;\n\npublic class Analyzer {\n    public boolean detectComplexity(String code) {\n        return code.contains("for") && code.contains("nested");\n    }\n}`
        },
        {
          path: 'Optimizer.java',
          source: `package com.axenix.demo;\n\npublic class Optimizer {\n    public String applyHashMapIndexing(String source) {\n        return source.replace("List", "Map");\n    }\n}`
        }
      ]
    }));
    return;
  }

  // POST /api/verify/stream (SSE)
  if (pathname === '/api/verify/stream' && req.method === 'POST') {
    res.writeHead(200, {
      ...corsHeaders,
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
    });

    const sendEvent = (event, data) => {
      res.write(`event: ${event}\ndata: ${typeof data === 'string' ? data : JSON.stringify(data)}\n\n`);
    };

    sendEvent('log', '[18:24:10] Инициализация изолированной песочницы Java 21...');
    setTimeout(() => sendEvent('log', '[18:24:10] Компиляция модифицированного кода через javax.tools.JavaCompiler: УСПЕШНО'), 400);
    setTimeout(() => sendEvent('log', '[18:24:11] Запуск Differential Oracle (I/O Equivalence Testing)...'), 800);
    setTimeout(() => sendEvent('log', '[18:24:11] [Test 1/12] testEmptyDataset: PASSED (12ms)'), 1100);
    setTimeout(() => sendEvent('log', '[18:24:11] [Test 4/12] testDuplicatesAndInvalid: PASSED (18ms)'), 1400);
    setTimeout(() => sendEvent('log', '[18:24:12] [Test 8/12] testBoundaryConditions: PASSED (32ms)'), 1700);
    setTimeout(() => sendEvent('log', '[18:24:12] [Test 12/12] testLargeDataset10k: PASSED (176ms)'), 2000);
    setTimeout(() => sendEvent('log', '[18:24:12] Smart Match (SORTED_LINES): 100% совпадение вывода с оригиналом ✓'), 2200);
    setTimeout(() => sendEvent('log', '[18:24:12] Замер производительности: 1240 мс -> 238 мс. Зафиксировано ускорение 5.2x!'), 2400);
    setTimeout(() => {
      sendEvent('complete', {
        id: 'rep-' + Date.now(),
        timestamp: new Date().toISOString(),
        allPassed: true,
        behaviorMatched: true,
        complexityBefore: 'O(n²)',
        complexityAfter: 'O(n log n)',
        speedup: '5.2x',
        originalTimeMs: 1240,
        optimizedTimeMs: 238,
        testResults: [
          { name: 'testEmptyDataset', status: 'PASSED', durationMs: 12, errorDetails: null },
          { name: 'testSingleOrder', status: 'PASSED', durationMs: 9, errorDetails: null },
          { name: 'testDuplicatesAndInvalid', status: 'PASSED', durationMs: 18, errorDetails: null },
          { name: 'testBoundaryConditions', status: 'PASSED', durationMs: 32, errorDetails: null },
          { name: 'testLargeDataset10k', status: 'PASSED', durationMs: 176, errorDetails: null },
          { name: 'testSpecialCharacters', status: 'PASSED', durationMs: 14, errorDetails: null },
          { name: 'testNullCustomerHandling', status: 'PASSED', durationMs: 11, errorDetails: null },
          { name: 'testConcurrentOrders', status: 'PASSED', durationMs: 25, errorDetails: null },
          { name: 'testMemoryConsumption', status: 'PASSED', durationMs: 40, errorDetails: null },
          { name: 'testSortingEquivalence', status: 'PASSED', durationMs: 19, errorDetails: null },
          { name: 'testOutputFormatting', status: 'PASSED', durationMs: 15, errorDetails: null },
          { name: 'testStressLoad', status: 'PASSED', durationMs: 210, errorDetails: null },
        ],
        logs: [
          '[18:24:10] Компиляция javax.tools: OK',
          '[18:24:11] Запуск тестов песочницы...',
          '[18:24:12] Все 12 тестов пройдены успешно.',
          '[18:24:12] Ускорение 5.2x подтверждено Quality Gate.'
        ]
      });
      res.end();
    }, 2600);
    return;
  }

  // Fallback 404
  res.writeHead(404, { ...corsHeaders, 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Not found', path: pathname }));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[Java Blueprints Backend] HTTP server listening on http://0.0.0.0:${PORT}`);
  console.log(`[Java Blueprints Backend] Endpoints: /api/health, /api/graph, /api/analysis, /api/optimize, /api/decompose, /api/verify/stream`);
});
