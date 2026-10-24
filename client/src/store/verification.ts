import { TestCaseResult, VerificationReport } from '../types/studio';

export function gatePassed(report: VerificationReport, requested: { name: string }[] = []): boolean {
  return report.behaviorMatched === true && report.allPassed === true
    && report.testResults.length > 0 && report.testResults.every(t => t.status === 'PASSED')
    && requested.every(t => report.testResults.some(r => r.name === t.name && r.status === 'PASSED'));
}

export function reportedTests(tests: TestCaseResult[], report: VerificationReport): TestCaseResult[] {
  // An empty user suite is run by the server as the explicit default stdin scenario.
  const requested = tests.length ? tests : report.testResults.map((r, i) => ({
    id: `default-${i}`, name: r.name, stdin: '', status: 'PENDING' as const, durationMs: 0,
  }));
  return requested.map(t => {
    const result = report.testResults.find(r => r.name === t.name);
    return { ...t, status: result?.status === 'PASSED' ? 'PASSED' : 'FAILED',
      durationMs: result?.durationMs ?? 0,
      errorDetails: report.compileOutput || result?.errorDetails || (result ? undefined : 'Результат теста отсутствует в отчёте'),
    };
  });
}
