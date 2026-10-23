# Design Systems - Sharp Edges

## Token Naming Conflicts Break Theme Switching

### **Id**
token-naming-conflicts
### **Severity**
CRITICAL
### **Description**
Inconsistent or conflicting token names make theming impossible
### **Symptoms**
  - Theme switch doesn't change all colors
  - Some components ignore theme
  - "color-blue" exists alongside "blue-primary"
  - Multiple tokens for same semantic purpose
### **Detection Pattern**
token|--color|--spacing|variable
### **Solution**
  Token Naming Conflict Resolution:
  
  The Problem:
  - color-blue-500 (primitive pretending to be semantic)
  - textPrimary (camelCase mixing)
  - --primary-color (different order)
  - blue (too generic)
  
  All four might mean the same thing. Chaos.
  
  The Fix - Strict Naming Convention:
  
  ```typescript
  // ENFORCE THIS PATTERN:
  // [category]-[property]-[variant]-[state]
  
  const VALID_PATTERNS = {
    color: /^color-(text|background|border|icon)-(primary|secondary|muted|inverse|error|success|warning)(-hover|-active|-disabled)?$/,
    spacing: /^spacing-(xs|sm|md|lg|xl|2xl)$/,
    font: /^font-(family|size|weight|line-height)-.+$/,
  };
  
  function validateTokenName(name: string): boolean {
    const category = name.split('-')[0];
    const pattern = VALID_PATTERNS[category];
    return pattern ? pattern.test(name) : false;
  }
  
  // Validation script
  function auditTokens(tokens: Record<string, any>): string[] {
    const issues: string[] = [];
  
    Object.keys(tokens).forEach(name => {
      if (!validateTokenName(name)) {
        issues.push(`Invalid token name: ${name}`);
      }
    });
  
    // Check for duplicates by value
    const byValue = new Map<string, string[]>();
    Object.entries(tokens).forEach(([name, value]) => {
      const existing = byValue.get(String(value)) || [];
      existing.push(name);
      byValue.set(String(value), existing);
    });
  
    byValue.forEach((names, value) => {
      if (names.length > 1) {
        issues.push(`Duplicate value ${value}: ${names.join(', ')}`);
      }
    });
  
    return issues;
  }
  ```
  
  Prevention:
  1. Lint token names in CI
  2. Single source of truth (Figma OR code, not both)
  3. Require PR approval for new tokens
  4. Document the naming convention prominently
  
### **References**
  - https://bradfrost.com/blog/post/naming-tokens-in-design-systems/

## Breaking Changes Without Deprecation Destroy Trust

### **Id**
breaking-changes-without-warning
### **Severity**
CRITICAL
### **Description**
Removing or changing APIs without notice breaks consumer codebases
### **Symptoms**
  - Teams stop upgrading the design system
  - Teams fork their own version
  - Angry Slack messages after updates
  - We'll just not use the design system
### **Detection Pattern**
upgrade|update|breaking|remove|deprecate
### **Solution**
  Breaking Change Protocol:
  
  NEVER DO THIS:
  ```typescript
  // v2.0.0 - Button
  <Button type="primary">Click</Button>
  
  // v3.0.0 - Surprise! API changed
  <Button variant="primary">Click</Button>  // Everything breaks
  ```
  
  ALWAYS DO THIS:
  
  ```typescript
  // v2.5.0 - Add deprecation warning
  interface ButtonProps {
    /**
     * @deprecated Use `variant` instead. Will be removed in v4.0.0
     */
    type?: 'primary' | 'secondary';
    variant?: 'primary' | 'secondary';
  }
  
  function Button({ type, variant, ...props }: ButtonProps) {
    if (type && process.env.NODE_ENV !== 'production') {
      console.warn(
        '[DesignSystem] Button: `type` prop is deprecated. ' +
        'Use `variant` instead. Migration: https://design.acme.com/migrate'
      );
    }
  
    const resolvedVariant = variant ?? type ?? 'primary';
    // ...
  }
  
  // v3.0.0 - Warning becomes error in dev
  if (type && process.env.NODE_ENV === 'development') {
    console.error('[DesignSystem] Button: `type` prop removed. Use `variant`.');
  }
  
  // v4.0.0 - Remove old prop (announced 6+ months ago)
  interface ButtonProps {
    variant: 'primary' | 'secondary';  // No more `type`
  }
  ```
  
  Deprecation Timeline:
  1. v2.5: Add warning, support both
  2. v3.0: Change warning to error in dev
  3. v3.5: Provide codemod
  4. v4.0: Remove (6+ months after deprecation)
  
  Always provide:
  - Migration guide with before/after
  - Codemod for automated updates
  - Clear timeline
  - Slack/email announcement
  
### **References**
  - https://semver.org/

## High Adoption Friction Kills Design Systems

### **Id**
adoption-friction
### **Severity**
HIGH
### **Description**
If it's hard to use, teams won't use it
### **Symptoms**
  - Low adoption metrics
  - Teams building custom components
  - The design system is too rigid
  - Long onboarding time for new developers
### **Detection Pattern**
install|setup|onboard|start|quick
### **Solution**
  Reduce Adoption Friction:
  
  Friction Points and Fixes:
  
  1. INSTALLATION FRICTION
  Bad: 15 peer dependencies, complex setup
  ```bash
  npm install @acme/design-system @acme/tokens @acme/icons
  npm install @emotion/react @emotion/styled framer-motion
  # Plus 10 more...
  ```
  
  Good: Single package, zero config
  ```bash
  npm install @acme/design-system
  ```
  
  2. IMPORT FRICTION
  Bad: Deep imports, multiple sources
  ```tsx
  import { Button } from '@acme/design-system/components/Button';
  import { useTheme } from '@acme/design-system/hooks';
  import { colors } from '@acme/tokens';
  ```
  
  Good: Single entry point
  ```tsx
  import { Button, useTheme, colors } from '@acme/design-system';
  ```
  
  3. CONFIGURATION FRICTION
  Bad: Required configuration before use
  ```tsx
  // Must set up theme provider, configure tokens, initialize context...
  <ThemeProvider theme={customTheme} tokens={tokenConfig} mode="light">
    <TokensProvider value={tokens}>
      <App />
    </TokensProvider>
  </ThemeProvider>
  ```
  
  Good: Works with zero config, customize if needed
  ```tsx
  // Works immediately
  <DesignSystemProvider>
    <App />
  </DesignSystemProvider>
  
  // Or customize
  <DesignSystemProvider theme="dark" brand="acme">
    <App />
  </DesignSystemProvider>
  ```
  
  4. DOCUMENTATION FRICTION
  Bad: Incomplete docs, no examples
  Good: Every component has:
  - Live playground
  - Copy-paste examples
  - Props table
  - Common patterns
  
  Adoption Checklist:
  - [ ] npm install is one command
  - [ ] First component works in < 5 minutes
  - [ ] No required configuration
  - [ ] Examples cover 80% of use cases
  - [ ] TypeScript autocomplete works
  
### **References**
  - https://bradfrost.com/blog/post/design-system-adoption/

## Theme Inheritance Creates Unpredictable Styles

### **Id**
theme-inheritance-issues
### **Severity**
HIGH
### **Description**
Nested themes or improper CSS specificity cause styling chaos
### **Symptoms**
  - Components look different in different parts of the app
  - Theme overrides don't work consistently
  - "Important" declarations everywhere
  - Nested dark/light themes behave strangely
### **Detection Pattern**
theme|inherit|nested|!important|specificity
### **Solution**
  Theme Inheritance Problems:
  
  The Issue:
  ```tsx
  // Nested themes = chaos
  <ThemeProvider theme="light">
    <Card>  {/* Light theme */}
      <ThemeProvider theme="dark">
        <Modal>  {/* Dark theme */}
          <ThemeProvider theme="light">
            <Tooltip>  {/* Light again? Or inherited? */}
            </Tooltip>
          </ThemeProvider>
        </Modal>
      </ThemeProvider>
    </Card>
  </ThemeProvider>
  ```
  
  CSS Custom Properties Solution:
  ```css
  /* Tokens scope to nearest theme ancestor */
  [data-theme="light"] {
    --color-background: white;
    --color-text: black;
  }
  
  [data-theme="dark"] {
    --color-background: #1a1a1a;
    --color-text: white;
  }
  
  /* Components use tokens, not raw values */
  .card {
    background: var(--color-background);
    color: var(--color-text);
  }
  ```
  
  Nested Theme Support:
  ```tsx
  function ThemeProvider({ theme, children }: { theme: 'light' | 'dark'; children: React.ReactNode }) {
    return (
      <div data-theme={theme} style={{ colorScheme: theme }}>
        {children}
      </div>
    );
  }
  
  // Usage - each section respects its theme
  <ThemeProvider theme="light">
    <MainContent />
    <ThemeProvider theme="dark">
      <Sidebar />  {/* Dark sidebar in light app */}
    </ThemeProvider>
  </ThemeProvider>
  ```
  
  Specificity Rules:
  ```css
  /* BAD: Specificity wars */
  .button { background: blue; }
  .dark .button { background: darkblue !important; }
  .modal .dark .button { background: navy !important !important; }  /* Doesn't work */
  
  /* GOOD: Token-based, no specificity issues */
  .button {
    background: var(--color-button-background);
  }
  /* Theme changes the variable, not the rule */
  ```
  
  Testing Nested Themes:
  ```tsx
  // Test every component in every theme combination
  const themes = ['light', 'dark'];
  const nesting = [1, 2, 3];  // Nesting levels
  
  themes.forEach(outer => {
    themes.forEach(inner => {
      test(`Component in ${outer} > ${inner}`, () => {
        render(
          <ThemeProvider theme={outer}>
            <ThemeProvider theme={inner}>
              <Button>Test</Button>
            </ThemeProvider>
          </ThemeProvider>
        );
        // Verify correct colors
      });
    });
  });
  ```
  
### **References**
  - https://css-tricks.com/theming-with-variables-globals-and-locals/

## Component API Bloat Makes Components Unusable

### **Id**
component-api-bloat
### **Severity**
HIGH
### **Description**
Too many props, variants, and options overwhelm users
### **Symptoms**
  - Components with 20+ props
  - Prop combinations that conflict
  - "How do I make it do X?" questions constantly
  - Documentation longer than the component
### **Detection Pattern**
props|interface|variant|option|config
### **Solution**
  API Bloat Prevention:
  
  Signs of Bloat:
  ```tsx
  // 30+ props = unusable
  <Button
    variant="primary"
    size="md"
    color="blue"
    hoverColor="darkblue"
    activeColor="navy"
    disabledColor="gray"
    textColor="white"
    borderRadius="md"
    borderWidth={1}
    borderColor="transparent"
    shadow="sm"
    hoverShadow="md"
    padding="md"
    paddingX="lg"
    paddingY="sm"
    fontSize="md"
    fontWeight="semibold"
    lineHeight="tight"
    leftIcon={<Plus />}
    rightIcon={<Arrow />}
    iconSpacing="sm"
    loading={false}
    loadingText="Loading..."
    loadingPosition="left"
    disabled={false}
    fullWidth={false}
    // ... 15 more props
  />
  ```
  
  Simplification Strategies:
  
  1. VARIANTS OVER PROPS
  ```tsx
  // Bad: Many boolean props
  <Button primary large rounded shadow />
  
  // Good: Variant enum
  <Button variant="primary" size="lg" />
  ```
  
  2. COMPOSITION OVER CONFIGURATION
  ```tsx
  // Bad: Every layout option as prop
  <Card
    headerTitle="Settings"
    headerAction={<Button>Edit</Button>}
    footerLeft={<Text>Updated today</Text>}
    footerRight={<Button>Save</Button>}
  />
  
  // Good: Compose with children
  <Card>
    <Card.Header>
      <Card.Title>Settings</Card.Title>
      <Button>Edit</Button>
    </Card.Header>
    <Card.Footer>
      <Text>Updated today</Text>
      <Button>Save</Button>
    </Card.Footer>
  </Card>
  ```
  
  3. SENSIBLE DEFAULTS
  ```tsx
  // Default everything reasonable
  interface ButtonProps {
    variant?: 'primary' | 'secondary' | 'ghost';  // default: 'primary'
    size?: 'sm' | 'md' | 'lg';  // default: 'md'
    // Only 2 props needed for 90% of uses
  }
  
  // Most buttons: <Button>Click</Button>
  // No props needed!
  ```
  
  4. PROGRESSIVE DISCLOSURE
  ```tsx
  // Simple API for simple uses
  <Select options={options} onChange={handleChange} />
  
  // Advanced API available when needed
  <Select
    options={options}
    onChange={handleChange}
    isMulti
    isSearchable
    customComponents={{ Option: CustomOption }}
  />
  ```
  
  Prop Limit Rule:
  - 0-5 props: Simple component, good
  - 6-10 props: Getting complex, review needed
  - 11-15 props: Too complex, split into variants
  - 16+ props: Redesign required
  
### **References**
  - https://react-spectrum.adobe.com/react-aria/

## Figma-Code Drift Creates Inconsistency

### **Id**
figma-code-drift
### **Severity**
MEDIUM
### **Description**
Design files and code diverge over time
### **Symptoms**
  - Designers and developers argue about "correct" values
  - Screenshots from Figma don't match production
  - Token values differ between design and code
  - "The spacing looks off" conversations
### **Detection Pattern**
figma|design.*file|token.*sync|style.*dictionary
### **Solution**
  Preventing Figma-Code Drift:
  
  Root Causes:
  1. Manual token entry in both places
  2. No sync process
  3. Designer changes without notifying devs
  4. Dev "fixes" without updating Figma
  
  Solution: Single Source of Truth
  
  Option A: Figma as Source
  ```yaml
  # .github/workflows/sync-from-figma.yml
  name: Sync Tokens from Figma
  
  on:
    schedule:
      - cron: '0 */4 * * *'  # Every 4 hours
    workflow_dispatch:
  
  jobs:
    sync:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@v4
  
        - name: Export from Figma
          run: npx figma-export tokens
          env:
            FIGMA_TOKEN: ${{ secrets.FIGMA_TOKEN }}
  
        - name: Build tokens
          run: npx style-dictionary build
  
        - name: Create PR if changed
          uses: peter-evans/create-pull-request@v5
          with:
            title: 'sync: design tokens from Figma'
            branch: figma-sync
  ```
  
  Option B: Code as Source
  ```yaml
  # Figma plugin reads from deployed tokens
  # Designers always see current production values
  ```
  
  Option C: External Source (Recommended)
  ```yaml
  # tokens.studio.json - Single source
  # Syncs to BOTH Figma and code
  {
    "color": {
      "primary": {
        "value": "#2563EB",
        "type": "color"
      }
    }
  }
  ```
  
  Drift Detection:
  ```typescript
  // drift-check.ts - Run in CI
  import figmaTokens from './figma-export.json';
  import codeTokens from './dist/tokens.json';
  
  function detectDrift(): string[] {
    const drift: string[] = [];
  
    Object.keys(figmaTokens).forEach(key => {
      if (figmaTokens[key] !== codeTokens[key]) {
        drift.push(`${key}: Figma=${figmaTokens[key]}, Code=${codeTokens[key]}`);
      }
    });
  
    return drift;
  }
  
  const drift = detectDrift();
  if (drift.length > 0) {
    console.error('Token drift detected:');
    drift.forEach(d => console.error(`  ${d}`));
    process.exit(1);
  }
  ```
  
### **References**
  - https://tokens.studio/

## Missing Component States Break Interactions

### **Id**
missing-component-states
### **Severity**
MEDIUM
### **Description**
Components missing hover, focus, disabled, or error states
### **Symptoms**
  - The button doesn't look clickable
  - No visual feedback on hover
  - Focus not visible for keyboard users
  - Error states undefined or inconsistent
### **Detection Pattern**
hover|focus|disabled|error|state|active
### **Solution**
  Complete State Coverage:
  
  Required States for Interactive Components:
  
  ```typescript
  // State checklist for Button
  const BUTTON_STATES = [
    'default',      // Resting state
    'hover',        // Mouse over
    'focus',        // Keyboard focus (visible ring)
    'active',       // Being clicked
    'disabled',     // Not interactive
    'loading',      // Async operation in progress
  ];
  
  // State checklist for Input
  const INPUT_STATES = [
    'default',      // Empty, no interaction
    'hover',        // Mouse over
    'focus',        // Active editing
    'filled',       // Has value
    'disabled',     // Not editable
    'readonly',     // Viewable, not editable
    'error',        // Validation failed
    'success',      // Validation passed (optional)
  ];
  
  // State checklist for Checkbox
  const CHECKBOX_STATES = [
    'unchecked',
    'checked',
    'indeterminate',  // Partial selection
    'hover',
    'focus',
    'disabled',
    'error',
  ];
  ```
  
  CSS Implementation:
  ```css
  .button {
    /* Default */
    background: var(--color-button-bg);
    color: var(--color-button-text);
  
    /* Hover - visible change */
    &:hover:not(:disabled) {
      background: var(--color-button-bg-hover);
    }
  
    /* Focus - ALWAYS visible, 2px+ ring */
    &:focus-visible {
      outline: 2px solid var(--color-focus-ring);
      outline-offset: 2px;
    }
  
    /* Active - feedback on click */
    &:active:not(:disabled) {
      transform: scale(0.98);
    }
  
    /* Disabled - reduced opacity, no pointer */
    &:disabled,
    &[aria-disabled="true"] {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }
  ```
  
  State Testing:
  ```tsx
  // Storybook story for all states
  export const AllStates: Story = {
    render: () => (
      <div className="grid grid-cols-3 gap-4">
        <Button>Default</Button>
        <Button className="pseudo-hover">Hover</Button>
        <Button className="pseudo-focus-visible">Focus</Button>
        <Button className="pseudo-active">Active</Button>
        <Button disabled>Disabled</Button>
        <Button loading>Loading</Button>
      </div>
    ),
  };
  
  // Visual regression test
  test('button states', async ({ page }) => {
    await page.goto('/storybook/button--all-states');
    await expect(page).toHaveScreenshot('button-states.png');
  });
  ```
  
### **References**
  - https://www.w3.org/WAI/ARIA/apg/patterns/

## Version Chaos Creates Dependency Hell

### **Id**
versioning-chaos
### **Severity**
MEDIUM
### **Description**
Multiple versions in same app, incompatible updates
### **Symptoms**
  - Which version should I use?
  - Different features available in different versions
  - Bundled twice due to version mismatch
  - Style conflicts between versions
### **Detection Pattern**
version|upgrade|dependency|peer
### **Solution**
  Version Management Strategy:
  
  The Problem:
  ```json
  // App's package.json
  {
    "dependencies": {
      "@acme/design-system": "^2.0.0"
    }
  }
  
  // Shared library also uses design system
  {
    "dependencies": {
      "@acme/design-system": "^1.5.0"  // Different version!
    }
  }
  
  // Result: Two versions bundled, styles conflict
  ```
  
  Solutions:
  
  1. PEER DEPENDENCIES
  ```json
  // Shared library
  {
    "peerDependencies": {
      "@acme/design-system": ">=1.5.0 <3.0.0"
    }
  }
  ```
  
  2. CSS SCOPING
  ```css
  /* Version namespace prevents conflicts */
  .ds-v2-button { ... }
  .ds-v3-button { ... }
  
  /* Or use CSS layers */
  @layer ds-v2, ds-v3;
  
  @layer ds-v2 {
    .button { ... }
  }
  ```
  
  3. SINGLETON PATTERN
  ```typescript
  // Ensure single instance across app
  declare global {
    interface Window {
      __ACME_DS_VERSION__?: string;
    }
  }
  
  if (window.__ACME_DS_VERSION__ && window.__ACME_DS_VERSION__ !== VERSION) {
    console.error(
      `Design system version conflict: ${window.__ACME_DS_VERSION__} vs ${VERSION}`
    );
  }
  window.__ACME_DS_VERSION__ = VERSION;
  ```
  
  4. VERSION COMPATIBILITY MATRIX
  ```markdown
  | DS Version | React | Emotion | Node |
  |------------|-------|---------|------|
  | 3.x        | >=18  | >=11    | >=18 |
  | 2.x        | >=17  | >=11    | >=16 |
  | 1.x        | >=16  | >=10    | >=14 |
  ```
  
  Release Strategy:
  - Major: Breaking changes, 6-month cycle
  - Minor: New features, monthly
  - Patch: Bug fixes, as needed
  - LTS: Support previous major for 12 months
  
### **References**
  - https://semver.org/

## Accessibility as Afterthought Creates Exclusion

### **Id**
accessibility-afterthought
### **Severity**
HIGH
### **Description**
Building components without accessibility from the start
### **Symptoms**
  - Failed accessibility audits
  - Keyboard navigation doesn't work
  - Screen readers announce wrong content
  - We'll add accessibility later
### **Detection Pattern**
a11y|accessibility|aria|screen.*reader|keyboard
### **Solution**
  Accessibility-First Development:
  
  The Problem:
  ```tsx
  // Inaccessible "button"
  <div onClick={handleClick} className="button">
    Click me
  </div>
  
  // Issues:
  // - Not focusable
  // - No keyboard activation
  // - No role announced
  // - No focus indicator
  ```
  
  The Solution:
  ```tsx
  // Accessible button
  <button
    type="button"
    onClick={handleClick}
    className="button"
  >
    Click me
  </button>
  
  // Or if custom element needed:
  <div
    role="button"
    tabIndex={0}
    onClick={handleClick}
    onKeyDown={(e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        handleClick();
      }
    }}
    className="button"
  >
    Click me
  </div>
  ```
  
  Accessibility Checklist per Component:
  
  ```markdown
  ## Pre-Ship A11y Checklist
  
  ### Keyboard
  - [ ] Focusable with Tab
  - [ ] Activatable with Enter/Space (buttons)
  - [ ] Escapable (modals, dropdowns)
  - [ ] Arrow key navigation (lists, menus)
  - [ ] Focus trap in modals
  - [ ] Focus visible (2px+ ring)
  
  ### Screen Reader
  - [ ] Semantic HTML or ARIA role
  - [ ] Accessible name (aria-label or content)
  - [ ] State announced (expanded, selected)
  - [ ] Live regions for dynamic content
  - [ ] Error messages linked to inputs
  
  ### Visual
  - [ ] 4.5:1 contrast (text)
  - [ ] 3:1 contrast (interactive)
  - [ ] Not color-only indicators
  - [ ] Respects prefers-reduced-motion
  - [ ] Works at 200% zoom
  
  ### Testing
  - [ ] axe-core passes
  - [ ] VoiceOver tested
  - [ ] NVDA tested
  - [ ] Keyboard-only tested
  ```
  
  Automated Testing:
  ```typescript
  // In every component test
  import { axe, toHaveNoViolations } from 'jest-axe';
  
  expect.extend(toHaveNoViolations);
  
  test('Button is accessible', async () => {
    const { container } = render(<Button>Click</Button>);
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });
  ```
  
### **References**
  - https://www.w3.org/WAI/WCAG21/quickref/