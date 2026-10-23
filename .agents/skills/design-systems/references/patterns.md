# Design Systems

## Patterns


---
  #### **Name**
Three-Tier Token Architecture
  #### **Description**
Structure tokens into primitive, semantic, and component layers
  #### **Detection**
token|variable|theme|color|spacing
  #### **When**
Setting up a new design system or restructuring tokens
  #### **Guidance**
    ## Token Architecture
    
    Tokens should flow from raw values to meaningful names to specific uses.
    
    ### Three-Tier Structure
    
    ```typescript
    // TIER 1: Primitive Tokens (raw values, no meaning)
    const primitives = {
      // Colors
      blue: {
        50: '#EFF6FF',
        100: '#DBEAFE',
        500: '#3B82F6',
        600: '#2563EB',
        900: '#1E3A8A',
      },
      gray: {
        50: '#F9FAFB',
        100: '#F3F4F6',
        500: '#6B7280',
        900: '#111827',
      },
      // Spacing
      space: {
        0: '0',
        1: '4px',
        2: '8px',
        3: '12px',
        4: '16px',
        6: '24px',
        8: '32px',
        12: '48px',
      },
    };
    
    // TIER 2: Semantic Tokens (meaning, theme-aware)
    const semantic = {
      color: {
        primary: primitives.blue[600],
        primaryHover: primitives.blue[700],
        background: primitives.gray[50],
        backgroundSubtle: primitives.gray[100],
        text: primitives.gray[900],
        textMuted: primitives.gray[500],
        border: primitives.gray[200],
        error: primitives.red[600],
        success: primitives.green[600],
      },
      spacing: {
        xs: primitives.space[1],   // 4px
        sm: primitives.space[2],   // 8px
        md: primitives.space[4],   // 16px
        lg: primitives.space[6],   // 24px
        xl: primitives.space[8],   // 32px
      },
    };
    
    // TIER 3: Component Tokens (specific uses)
    const component = {
      button: {
        background: semantic.color.primary,
        backgroundHover: semantic.color.primaryHover,
        text: '#FFFFFF',
        paddingX: semantic.spacing.md,
        paddingY: semantic.spacing.sm,
        borderRadius: primitives.radius.md,
      },
      input: {
        background: semantic.color.background,
        border: semantic.color.border,
        borderFocus: semantic.color.primary,
        text: semantic.color.text,
        placeholder: semantic.color.textMuted,
      },
    };
    ```
    
    ### Benefits of Three Tiers
    
    | Tier | Changes When | Example |
    |------|--------------|---------|
    | Primitive | Brand refresh | blue-500 becomes #0066FF |
    | Semantic | Theme switch | primary maps to different primitive |
    | Component | Component redesign | button.paddingX changes |
    
    Theme switching only touches Tier 2. Brand refresh only touches Tier 1.
    
  #### **Success Rate**
Teams with proper token architecture report 80% faster theme changes

---
  #### **Name**
Composition Over Configuration
  #### **Description**
Build flexible components through composition, not prop explosion
  #### **Detection**
component|variant|prop|api
  #### **When**
Designing component APIs
  #### **Guidance**
    ## Component Composition
    
    Don't build one component with 50 props. Build composable primitives.
    
    ### Bad: Prop Explosion
    
    ```tsx
    // DON'T: One component with endless props
    <Card
      title="Settings"
      titleSize="large"
      subtitle="Manage your preferences"
      showDivider
      footerAlign="right"
      headerIcon={<SettingsIcon />}
      headerAction={<Button>Edit</Button>}
      footerPrimary={<Button>Save</Button>}
      footerSecondary={<Button variant="ghost">Cancel</Button>}
      loading={isLoading}
      error={error}
      variant="elevated"
      padding="large"
      // ... 20 more props
    />
    ```
    
    ### Good: Composition
    
    ```tsx
    // DO: Composable primitives
    <Card variant="elevated">
      <Card.Header>
        <Card.Icon><SettingsIcon /></Card.Icon>
        <Card.Title>Settings</Card.Title>
        <Card.Action><Button size="sm">Edit</Button></Card.Action>
      </Card.Header>
    
      <Card.Content>
        <Card.Subtitle>Manage your preferences</Card.Subtitle>
        {/* Flexible content area */}
      </Card.Content>
    
      <Card.Footer align="right">
        <Button variant="ghost">Cancel</Button>
        <Button>Save</Button>
      </Card.Footer>
    </Card>
    ```
    
    ### Composition Patterns
    
    ```tsx
    // 1. Compound Components (Card.Header, Card.Content, etc.)
    <Tabs>
      <Tabs.List>
        <Tabs.Tab>One</Tabs.Tab>
        <Tabs.Tab>Two</Tabs.Tab>
      </Tabs.List>
      <Tabs.Panels>
        <Tabs.Panel>Content 1</Tabs.Panel>
        <Tabs.Panel>Content 2</Tabs.Panel>
      </Tabs.Panels>
    </Tabs>
    
    // 2. Render Props (maximum flexibility)
    <Listbox>
      {({ open, selected }) => (
        <Listbox.Button>{selected?.label}</Listbox.Button>
        <Listbox.Options>
          {options.map(opt => (
            <Listbox.Option key={opt.id} value={opt}>
              {({ active, selected }) => (
                <span className={active ? 'bg-blue-100' : ''}>
                  {opt.label}
                </span>
              )}
            </Listbox.Option>
          ))}
        </Listbox.Options>
      )}
    </Listbox>
    
    // 3. Slots (explicit composition points)
    <Dialog>
      <Dialog.Trigger asChild>
        <Button>Open</Button>
      </Dialog.Trigger>
      <Dialog.Content>
        <Dialog.Title>Are you sure?</Dialog.Title>
        <Dialog.Description>This action cannot be undone.</Dialog.Description>
        <Dialog.Actions>
          <Dialog.Close asChild><Button variant="ghost">Cancel</Button></Dialog.Close>
          <Button variant="destructive">Delete</Button>
        </Dialog.Actions>
      </Dialog.Content>
    </Dialog>
    ```
    
    ### Composition Rules
    
    1. Max 5-7 props before considering composition
    2. Any "leftIcon/rightIcon" pattern should use slots
    3. Complex layouts always use compound components
    4. Render props for maximum consumer flexibility
    
  #### **Success Rate**
Composable APIs reduce component issues by 60%

---
  #### **Name**
Semantic Naming Convention
  #### **Description**
Use consistent, meaningful names across the entire system
  #### **Detection**
naming|convention|token.*name|variable.*name
  #### **When**
Establishing naming conventions or reviewing token names
  #### **Guidance**
    ## Naming Conventions
    
    Names should be predictable, scannable, and self-documenting.
    
    ### Naming Formula
    
    ```
    [category]-[property]-[variant]-[state]
    
    Examples:
    color-text-primary         // Primary text color
    color-text-primary-hover   // Primary text on hover
    color-background-subtle    // Subtle background
    spacing-component-padding  // Component internal padding
    ```
    
    ### Naming Patterns by Category
    
    ```yaml
    # Colors
    color-text-{variant}
    color-background-{variant}
    color-border-{variant}
    color-icon-{variant}
    
    variants: primary, secondary, muted, inverse, error, success, warning
    
    # Spacing
    spacing-{size}                    # Generic spacing
    spacing-component-{property}      # Component-specific
    spacing-layout-{property}         # Layout-specific
    
    sizes: xs, sm, md, lg, xl, 2xl
    properties: gap, padding, margin
    
    # Typography
    font-family-{variant}             # sans, mono, serif
    font-size-{scale}                 # xs, sm, md, lg, xl, 2xl, 3xl
    font-weight-{variant}             # normal, medium, semibold, bold
    line-height-{variant}             # tight, normal, relaxed
    
    # Effects
    shadow-{size}                     # sm, md, lg, xl
    radius-{size}                     # none, sm, md, lg, full
    opacity-{variant}                 # disabled, hover, overlay
    ```
    
    ### Anti-Patterns in Naming
    
    | Bad | Good | Why |
    |-----|------|-----|
    | `blue-500` | `color-primary` | Semantic, not literal |
    | `margin-12` | `spacing-lg` | Scale-based, not pixel |
    | `btnBg` | `color-button-background` | Readable, not abbreviated |
    | `gray3` | `color-text-muted` | Meaningful, not indexed |
    | `p-4` | `spacing-component-padding` | Intent, not shorthand |
    
    ### Naming Validation
    
    ```typescript
    const NAMING_RULES = {
      // Must be kebab-case
      pattern: /^[a-z]+(-[a-z0-9]+)*$/,
    
      // Must start with category
      categories: ['color', 'spacing', 'font', 'shadow', 'radius', 'animation'],
    
      // No color values in names
      forbidden: ['blue', 'red', 'gray', 'px', 'rem'],
    
      // Max segments
      maxSegments: 4,
    };
    
    function validateTokenName(name: string): boolean {
      const segments = name.split('-');
      return (
        NAMING_RULES.pattern.test(name) &&
        NAMING_RULES.categories.includes(segments[0]) &&
        !NAMING_RULES.forbidden.some(f => name.includes(f)) &&
        segments.length <= NAMING_RULES.maxSegments
      );
    }
    ```
    
  #### **Success Rate**
Consistent naming reduces token discovery time by 70%

---
  #### **Name**
Component Documentation Standard
  #### **Description**
Document every component with props, examples, and accessibility notes
  #### **Detection**
document|storybook|readme|usage
  #### **When**
Creating or improving component documentation
  #### **Guidance**
    ## Documentation Standard
    
    Documentation determines adoption. Incomplete docs = unused components.
    
    ### Required Documentation Sections
    
    ```markdown
    # Button
    
    Buttons trigger actions or navigate to new pages.
    
    ## Usage
    
    \`\`\`tsx
    import { Button } from '@acme/design-system';
    
    <Button variant="primary" size="md">
      Click me
    </Button>
    \`\`\`
    
    ## Props
    
    | Prop | Type | Default | Description |
    |------|------|---------|-------------|
    | variant | 'primary' \| 'secondary' \| 'ghost' \| 'destructive' | 'primary' | Visual style |
    | size | 'sm' \| 'md' \| 'lg' | 'md' | Button size |
    | disabled | boolean | false | Disable interaction |
    | loading | boolean | false | Show loading spinner |
    | leftIcon | ReactNode | - | Icon before label |
    | rightIcon | ReactNode | - | Icon after label |
    | asChild | boolean | false | Merge props to child |
    
    ## Examples
    
    ### Variants
    
    \`\`\`tsx
    <Button variant="primary">Primary</Button>
    <Button variant="secondary">Secondary</Button>
    <Button variant="ghost">Ghost</Button>
    <Button variant="destructive">Delete</Button>
    \`\`\`
    
    ### With Icons
    
    \`\`\`tsx
    <Button leftIcon={<PlusIcon />}>Add item</Button>
    <Button rightIcon={<ArrowRightIcon />}>Continue</Button>
    \`\`\`
    
    ### Loading State
    
    \`\`\`tsx
    <Button loading>Saving...</Button>
    \`\`\`
    
    ## Accessibility
    
    - Uses native `<button>` element
    - Disabled state uses `aria-disabled` (keeps focus)
    - Loading state announces via `aria-live`
    - Minimum 44x44px touch target
    
    ## Do's and Don'ts
    
    | Do | Don't |
    |----|-------|
    | Use clear, action-oriented labels | Use vague labels like "Click here" |
    | Use destructive variant for dangerous actions | Use red color without variant |
    | Disable during async operations | Leave enabled during loading |
    | Use icons to reinforce meaning | Use icons without labels |
    ```
    
    ### Storybook Structure
    
    ```typescript
    // Button.stories.tsx
    import type { Meta, StoryObj } from '@storybook/react';
    import { Button } from './Button';
    
    const meta: Meta<typeof Button> = {
      title: 'Components/Button',
      component: Button,
      tags: ['autodocs'],
      argTypes: {
        variant: {
          control: 'select',
          options: ['primary', 'secondary', 'ghost', 'destructive'],
        },
        size: {
          control: 'select',
          options: ['sm', 'md', 'lg'],
        },
      },
    };
    
    export default meta;
    type Story = StoryObj<typeof Button>;
    
    export const Primary: Story = {
      args: {
        children: 'Primary Button',
        variant: 'primary',
      },
    };
    
    export const AllVariants: Story = {
      render: () => (
        <div className="flex gap-4">
          <Button variant="primary">Primary</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="destructive">Destructive</Button>
        </div>
      ),
    };
    
    export const WithIcon: Story = {
      args: {
        children: 'Add Item',
        leftIcon: <PlusIcon />,
      },
    };
    ```
    
  #### **Success Rate**
Well-documented systems see 3x higher adoption rates

---
  #### **Name**
Version and Deprecation Strategy
  #### **Description**
Handle component evolution without breaking consumers
  #### **Detection**
version|deprecate|breaking|migration
  #### **When**
Updating existing components or planning system evolution
  #### **Guidance**
    ## Versioning Strategy
    
    Changes should improve the system without disrupting teams.
    
    ### Semver for Design Systems
    
    ```yaml
    MAJOR (1.0.0 → 2.0.0):
      - Breaking component API changes
      - Token removals
      - Required prop additions
      - Complete redesigns
    
    MINOR (1.0.0 → 1.1.0):
      - New components
      - New optional props
      - New token additions
      - Visual updates (non-breaking)
    
    PATCH (1.0.0 → 1.0.1):
      - Bug fixes
      - Accessibility improvements
      - Documentation updates
    ```
    
    ### Deprecation Process
    
    ```typescript
    // 1. Mark deprecated with warning
    interface ButtonProps {
      /**
       * @deprecated Use `variant="ghost"` instead. Will be removed in v3.0.
       */
      outline?: boolean;
      variant?: 'primary' | 'secondary' | 'ghost' | 'destructive';
    }
    
    function Button({ outline, variant, ...props }: ButtonProps) {
      // 2. Show runtime warning in development
      if (outline && process.env.NODE_ENV === 'development') {
        console.warn(
          '[DesignSystem] Button: `outline` prop is deprecated. ' +
          'Use `variant="ghost"` instead. ' +
          'See migration guide: https://design.acme.com/migration/button'
        );
      }
    
      // 3. Support both during transition
      const resolvedVariant = outline ? 'ghost' : variant;
    
      return <button {...props} />;
    }
    
    // 4. Provide codemod for automated migration
    // npx @acme/design-system-codemods button-outline-to-variant
    ```
    
    ### Migration Guide Template
    
    ```markdown
    # Migrating Button from v2 to v3
    
    ## Breaking Changes
    
    ### `outline` prop removed
    
    **Before (v2):**
    \`\`\`tsx
    <Button outline>Ghost button</Button>
    \`\`\`
    
    **After (v3):**
    \`\`\`tsx
    <Button variant="ghost">Ghost button</Button>
    \`\`\`
    
    ### Automated Migration
    
    \`\`\`bash
    npx @acme/design-system-codemods@latest button-v3
    \`\`\`
    
    ## Deprecation Timeline
    
    | Version | Status | Date |
    |---------|--------|------|
    | v2.5.0 | Deprecated with warning | 2024-01-15 |
    | v2.8.0 | Console error | 2024-03-01 |
    | v3.0.0 | Removed | 2024-06-01 |
    ```
    
    ### Token Deprecation
    
    ```css
    /* tokens.css */
    
    /* Current (keep) */
    --color-primary: #2563EB;
    
    /* Deprecated (warn then remove) */
    --color-blue-primary: var(--color-primary); /* @deprecated use --color-primary */
    ```
    
  #### **Success Rate**
Clear deprecation processes reduce upgrade friction by 80%

---
  #### **Name**
Multi-Theme Architecture
  #### **Description**
Support multiple themes through token layering
  #### **Detection**
theme|dark.*mode|multi.*brand|white.*label
  #### **When**
Adding dark mode, multi-brand support, or white-labeling
  #### **Guidance**
    ## Multi-Theme Architecture
    
    Design for theme switching from the start - retrofitting is painful.
    
    ### CSS Custom Property Approach
    
    ```css
    /* 1. Base tokens (primitives) */
    :root {
      /* Primitives - don't change per theme */
      --blue-500: #3B82F6;
      --blue-600: #2563EB;
      --gray-50: #F9FAFB;
      --gray-900: #111827;
    }
    
    /* 2. Light theme (default) */
    :root,
    [data-theme="light"] {
      --color-background: var(--gray-50);
      --color-background-elevated: #FFFFFF;
      --color-text: var(--gray-900);
      --color-text-muted: var(--gray-500);
      --color-primary: var(--blue-600);
      --color-border: var(--gray-200);
    }
    
    /* 3. Dark theme */
    [data-theme="dark"] {
      --color-background: var(--gray-900);
      --color-background-elevated: var(--gray-800);
      --color-text: var(--gray-50);
      --color-text-muted: var(--gray-400);
      --color-primary: var(--blue-500);
      --color-border: var(--gray-700);
    }
    
    /* 4. Brand themes (multi-tenant) */
    [data-brand="acme"] {
      --color-primary: #FF6B00;
      --color-primary-hover: #E56000;
    }
    
    [data-brand="megacorp"] {
      --color-primary: #00875A;
      --color-primary-hover: #006644;
    }
    ```
    
    ### Theme Provider Pattern
    
    ```tsx
    // ThemeProvider.tsx
    import { createContext, useContext, useState, useEffect } from 'react';
    
    type Theme = 'light' | 'dark' | 'system';
    type Brand = 'default' | 'acme' | 'megacorp';
    
    interface ThemeContext {
      theme: Theme;
      resolvedTheme: 'light' | 'dark';
      brand: Brand;
      setTheme: (theme: Theme) => void;
      setBrand: (brand: Brand) => void;
    }
    
    const ThemeContext = createContext<ThemeContext | null>(null);
    
    export function ThemeProvider({ children }: { children: React.ReactNode }) {
      const [theme, setTheme] = useState<Theme>('system');
      const [brand, setBrand] = useState<Brand>('default');
      const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>('light');
    
      useEffect(() => {
        // Handle system preference
        if (theme === 'system') {
          const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
          setResolvedTheme(mediaQuery.matches ? 'dark' : 'light');
    
          const handler = (e: MediaQueryListEvent) => {
            setResolvedTheme(e.matches ? 'dark' : 'light');
          };
          mediaQuery.addEventListener('change', handler);
          return () => mediaQuery.removeEventListener('change', handler);
        } else {
          setResolvedTheme(theme);
        }
      }, [theme]);
    
      useEffect(() => {
        // Apply to DOM
        document.documentElement.dataset.theme = resolvedTheme;
        document.documentElement.dataset.brand = brand;
      }, [resolvedTheme, brand]);
    
      return (
        <ThemeContext.Provider value={{ theme, resolvedTheme, brand, setTheme, setBrand }}>
          {children}
        </ThemeContext.Provider>
      );
    }
    
    export const useTheme = () => {
      const context = useContext(ThemeContext);
      if (!context) throw new Error('useTheme must be used within ThemeProvider');
      return context;
    };
    ```
    
    ### Theme Testing Checklist
    
    | Check | Light | Dark | Brand A | Brand B |
    |-------|-------|------|---------|---------|
    | Text contrast | 4.5:1+ | 4.5:1+ | 4.5:1+ | 4.5:1+ |
    | Focus visible | Yes | Yes | Yes | Yes |
    | Interactive states | All | All | All | All |
    | Charts/graphs | Clear | Clear | Clear | Clear |
    | Images/icons | Clear | Clear | Clear | Clear |
    
  #### **Success Rate**
Token-based theming enables theme switches in hours, not weeks

---
  #### **Name**
Figma-to-Code Synchronization
  #### **Description**
Keep design files and code in sync through automation
  #### **Detection**
figma|sync|design.*token|style.*dictionary
  #### **When**
Setting up design-to-code pipeline or fixing drift
  #### **Guidance**
    ## Figma-to-Code Pipeline
    
    Design files and code should be a single source of truth, not two.
    
    ### Token Export from Figma
    
    ```typescript
    // figma-export.ts
    import * as Figma from 'figma-js';
    import StyleDictionary from 'style-dictionary';
    
    interface FigmaTokens {
      colors: Record<string, string>;
      spacing: Record<string, string>;
      typography: Record<string, any>;
    }
    
    async function exportTokensFromFigma(fileKey: string): Promise<FigmaTokens> {
      const client = Figma.Client({ personalAccessToken: process.env.FIGMA_TOKEN });
      const { data } = await client.file(fileKey);
    
      // Extract color styles
      const colors: Record<string, string> = {};
      Object.values(data.styles).forEach((style) => {
        if (style.styleType === 'FILL') {
          // Map Figma style name to token name
          const tokenName = style.name
            .toLowerCase()
            .replace(/\//g, '-')
            .replace(/\s+/g, '-');
          colors[tokenName] = rgbToHex(style.color);
        }
      });
    
      return { colors, spacing: {}, typography: {} };
    }
    
    // Transform to Style Dictionary format
    function toStyleDictionary(tokens: FigmaTokens) {
      return {
        color: Object.entries(tokens.colors).reduce((acc, [name, value]) => {
          const parts = name.split('-');
          let current = acc;
          parts.forEach((part, i) => {
            if (i === parts.length - 1) {
              current[part] = { value };
            } else {
              current[part] = current[part] || {};
              current = current[part];
            }
          });
          return acc;
        }, {}),
      };
    }
    ```
    
    ### Style Dictionary Configuration
    
    ```javascript
    // style-dictionary.config.js
    module.exports = {
      source: ['tokens/**/*.json'],
      platforms: {
        css: {
          transformGroup: 'css',
          buildPath: 'dist/css/',
          files: [{
            destination: 'tokens.css',
            format: 'css/variables',
            options: {
              outputReferences: true,
            },
          }],
        },
        js: {
          transformGroup: 'js',
          buildPath: 'dist/js/',
          files: [{
            destination: 'tokens.js',
            format: 'javascript/es6',
          }],
        },
        scss: {
          transformGroup: 'scss',
          buildPath: 'dist/scss/',
          files: [{
            destination: '_tokens.scss',
            format: 'scss/variables',
          }],
        },
        ios: {
          transformGroup: 'ios-swift',
          buildPath: 'dist/ios/',
          files: [{
            destination: 'Tokens.swift',
            format: 'ios-swift/class.swift',
          }],
        },
        android: {
          transformGroup: 'android',
          buildPath: 'dist/android/',
          files: [{
            destination: 'tokens.xml',
            format: 'android/resources',
          }],
        },
      },
    };
    ```
    
    ### CI/CD Integration
    
    ```yaml
    # .github/workflows/tokens-sync.yml
    name: Sync Design Tokens
    
    on:
      schedule:
        - cron: '0 */6 * * *'  # Every 6 hours
      workflow_dispatch:
    
    jobs:
      sync:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
    
          - name: Export from Figma
            run: npm run figma:export
            env:
              FIGMA_TOKEN: ${{ secrets.FIGMA_TOKEN }}
              FIGMA_FILE_KEY: ${{ secrets.FIGMA_FILE_KEY }}
    
          - name: Build tokens
            run: npm run tokens:build
    
          - name: Create PR if changed
            uses: peter-evans/create-pull-request@v5
            with:
              title: 'chore: sync design tokens from Figma'
              commit-message: 'chore: sync design tokens'
              branch: tokens/figma-sync
              delete-branch: true
    ```
    
  #### **Success Rate**
Automated sync eliminates design-code drift

---
  #### **Name**
Component Audit System
  #### **Description**
Track component usage and identify unused or overused components
  #### **Detection**
audit|usage|analytics|adoption
  #### **When**
Understanding system adoption or planning deprecations
  #### **Guidance**
    ## Component Usage Tracking
    
    Know which components are used, where, and how.
    
    ### Usage Tracking Implementation
    
    ```typescript
    // tracking.ts
    interface ComponentUsage {
      component: string;
      props: Record<string, any>;
      file: string;
      timestamp: number;
    }
    
    // Development-only tracking
    if (process.env.NODE_ENV === 'development') {
      const usageData: ComponentUsage[] = [];
    
      export function trackComponent(
        component: string,
        props: Record<string, any>,
        file: string
      ) {
        usageData.push({
          component,
          props,
          file,
          timestamp: Date.now(),
        });
      }
    
      // Report on command
      (window as any).__DS_REPORT__ = () => {
        const report = usageData.reduce((acc, { component, props }) => {
          acc[component] = acc[component] || { count: 0, variants: {} };
          acc[component].count++;
    
          // Track variant usage
          if (props.variant) {
            acc[component].variants[props.variant] =
              (acc[component].variants[props.variant] || 0) + 1;
          }
    
          return acc;
        }, {} as Record<string, any>);
    
        console.table(report);
        return report;
      };
    }
    
    // HOC for tracking
    export function withTracking<P extends object>(
      Component: React.ComponentType<P>,
      componentName: string
    ) {
      return function TrackedComponent(props: P) {
        useEffect(() => {
          if (process.env.NODE_ENV === 'development') {
            trackComponent(componentName, props as Record<string, any>, 'unknown');
          }
        }, []);
    
        return <Component {...props} />;
      };
    }
    ```
    
    ### Audit Report Structure
    
    ```markdown
    # Design System Audit Report
    Generated: 2024-01-15
    
    ## Summary
    
    | Metric | Value |
    |--------|-------|
    | Total Components | 47 |
    | Used Components | 38 (81%) |
    | Unused Components | 9 (19%) |
    | Deprecated in Use | 3 |
    
    ## Most Used Components
    
    | Component | Uses | Files |
    |-----------|------|-------|
    | Button | 847 | 124 |
    | Text | 623 | 98 |
    | Card | 412 | 67 |
    | Input | 389 | 45 |
    
    ## Unused Components (Deprecation Candidates)
    
    - Accordion (0 uses) - Remove in v3
    - Toast (0 uses) - Teams using react-hot-toast instead
    - Pagination (2 uses) - Replace with InfiniteScroll
    
    ## Variant Analysis
    
    ### Button Variants
    | Variant | Uses | Percentage |
    |---------|------|------------|
    | primary | 412 | 49% |
    | secondary | 234 | 28% |
    | ghost | 156 | 18% |
    | destructive | 45 | 5% |
    
    ## Recommendations
    
    1. **Remove Accordion** - No adoption, teams prefer custom solutions
    2. **Deprecate Toast** - Teams have chosen react-hot-toast
    3. **Add IconButton** - 47 instances of icon-only Button pattern found
    ```
    
  #### **Success Rate**
Usage data makes deprecation decisions objective

---
  #### **Name**
Accessibility-First Components
  #### **Description**
Build accessibility into components from the start
  #### **Detection**
accessibility|a11y|aria|keyboard|screen.*reader
  #### **When**
Creating new components or auditing existing ones
  #### **Guidance**
    ## Accessibility-First Design
    
    Accessibility is not a feature - it's a requirement.
    
    ### Component Accessibility Checklist
    
    ```markdown
    ## Before Shipping Any Component
    
    ### Keyboard
    - [ ] All interactive elements focusable
    - [ ] Focus order logical (tab/shift+tab)
    - [ ] Focus visible (2px+ outline)
    - [ ] Escape closes overlays
    - [ ] Enter/Space activate buttons
    - [ ] Arrow keys for composite widgets
    
    ### Screen Reader
    - [ ] Semantic HTML used (button, not div)
    - [ ] Labels present and descriptive
    - [ ] States announced (expanded, selected, disabled)
    - [ ] Live regions for dynamic content
    - [ ] Headings hierarchy logical
    
    ### Visual
    - [ ] Color contrast 4.5:1+ (text)
    - [ ] Color contrast 3:1+ (interactive)
    - [ ] Not color-only indicators
    - [ ] Text resizable to 200%
    - [ ] Reduced motion respected
    ```
    
    ### Accessible Component Example
    
    ```tsx
    // Button.tsx - Fully accessible
    import { forwardRef } from 'react';
    
    interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
      variant?: 'primary' | 'secondary' | 'ghost' | 'destructive';
      size?: 'sm' | 'md' | 'lg';
      loading?: boolean;
    }
    
    export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
      ({ variant = 'primary', size = 'md', loading, disabled, children, ...props }, ref) => {
        // Use aria-disabled instead of disabled for focus retention
        const isDisabled = disabled || loading;
    
        return (
          <button
            ref={ref}
            type="button"
            aria-disabled={isDisabled}
            aria-busy={loading}
            className={cn(
              // Base styles
              'inline-flex items-center justify-center font-medium',
              'transition-colors focus-visible:outline-none',
              'focus-visible:ring-2 focus-visible:ring-offset-2',
              // Disabled uses opacity, not different colors
              isDisabled && 'opacity-50 cursor-not-allowed',
              // Size-based touch targets (min 44x44)
              size === 'sm' && 'min-h-[36px] px-3 text-sm', // Still touchable
              size === 'md' && 'min-h-[44px] px-4 text-base',
              size === 'lg' && 'min-h-[52px] px-6 text-lg',
            )}
            onClick={isDisabled ? undefined : props.onClick}
            {...props}
          >
            {loading && (
              <span className="mr-2" aria-hidden="true">
                <Spinner size="sm" />
              </span>
            )}
            {children}
            {loading && (
              <span className="sr-only">Loading, please wait...</span>
            )}
          </button>
        );
      }
    );
    
    Button.displayName = 'Button';
    ```
    
    ### Required ARIA Patterns by Component
    
    | Component | Required ARIA |
    |-----------|---------------|
    | Dialog | role="dialog", aria-modal, aria-labelledby |
    | Menu | role="menu", role="menuitem", aria-expanded |
    | Tabs | role="tablist", role="tab", role="tabpanel" |
    | Combobox | role="combobox", aria-autocomplete, aria-expanded |
    | Toast | role="alert" or role="status", aria-live |
    | Tooltip | role="tooltip", aria-describedby |
    | Dropdown | role="listbox", role="option", aria-selected |
    
  #### **Success Rate**
A11y-first components reduce accessibility bugs by 90%

## Anti-Patterns


---
  #### **Name**
Inconsistent Token Naming
  #### **Description**
Using different naming patterns across the token system
  #### **Detection**
token|variable|--.*color|--.*spacing
  #### **Why Harmful**
    Inconsistent naming makes tokens impossible to discover and use correctly.
    Teams create duplicate tokens, workarounds proliferate, and the system fragments.
    
  #### **What To Do**
    Establish and enforce a naming convention from day one:
    - category-property-variant-state
    - color-text-primary, color-background-subtle
    - Never: blue-500, textColor, bgGray, colorPrimary
    

---
  #### **Name**
No Component Documentation
  #### **Description**
Shipping components without usage examples and API documentation
  #### **Detection**
component|export.*function|export.*const
  #### **Why Harmful**
    Undocumented components don't get used. Teams will build their own
    or misuse what exists. Documentation is not optional - it's 50% of
    whether a design system succeeds.
    
  #### **What To Do**
    Every component needs:
    - Clear description of purpose
    - Props table with types and defaults
    - Usage examples covering common cases
    - Accessibility notes
    - Do's and Don'ts
    

---
  #### **Name**
Tightly Coupled Components
  #### **Description**
Components that only work together or have hidden dependencies
  #### **Detection**
import.*from.*component|require.*component
  #### **Why Harmful**
    Tightly coupled components can't be used independently, create bundle
    size issues, and make the system rigid. Teams end up importing the
    whole system for one button.
    
  #### **What To Do**
    - Each component should be independently usable
    - Use composition over coupling
    - Lazy load compound components
    - Tree-shake friendly exports
    

---
  #### **Name**
Breaking Changes Without Migration Path
  #### **Description**
Removing or changing APIs without deprecation warnings or codemods
  #### **Detection**
remove|delete|breaking|major
  #### **Why Harmful**
    Breaking changes without migration paths destroy trust. Teams stop
    updating, fork the system, or abandon it entirely. One bad upgrade
    experience can kill adoption.
    
  #### **What To Do**
    - Deprecate with warnings first (3+ months)
    - Provide codemods for automated migration
    - Document every breaking change
    - Support old API during transition period
    

---
  #### **Name**
Hardcoded Values Instead of Tokens
  #### **Description**
Using raw values like
  #### **Detection**
#[0-9a-fA-F]{6}|\d+px|rgb|rgba
  #### **Why Harmful**
    Hardcoded values bypass the system. They don't respond to theme changes,
    create inconsistency, and make maintenance impossible. One hardcoded
    color means theme switching is broken.
    
  #### **What To Do**
    Use tokens everywhere:
    - color: var(--color-primary) not #3B82F6
    - padding: var(--spacing-md) not 16px
    - Lint for hardcoded values
    - Fail CI on token violations
    

---
  #### **Name**
Over-Engineered Component APIs
  #### **Description**
Components with 30+ props trying to handle every use case
  #### **Detection**
props|interface|type.*Props
  #### **Why Harmful**
    Complex APIs have steep learning curves, confusing documentation, and
    are impossible to maintain. Teams use 10% of props, the rest is bloat.
    Every prop is a maintenance burden.
    
  #### **What To Do**
    - Max 5-7 props before composition
    - Use compound components for complex UIs
    - Provide sensible defaults
    - Separate variants instead of boolean props
    

---
  #### **Name**
Ignoring Browser/Device Diversity
  #### **Description**
Only testing on Chrome desktop with fast internet
  #### **Detection**
responsive|mobile|safari|firefox|edge
  #### **Why Harmful**
    Real users have old phones, Safari quirks, slow connections, and
    different screen sizes. A system that only works on Chrome desktop
    isn't a system - it's a demo.
    
  #### **What To Do**
    Test matrix:
    - Chrome, Safari, Firefox, Edge
    - Mobile Safari, Chrome Android
    - Slow 3G simulation
    - Screen readers (VoiceOver, NVDA)
    - Keyboard-only navigation
    

---
  #### **Name**
Token Sprawl Without Governance
  #### **Description**
Adding tokens without process, review, or cleanup
  #### **Detection**
token|variable|add|new
  #### **Why Harmful**
    Ungoverned tokens multiply. 50 becomes 500, naming diverges, duplicates
    appear, and nobody knows which to use. Token sprawl makes the system
    unusable.
    
  #### **What To Do**
    - Require approval for new tokens
    - Regular token audits (quarterly)
    - Document why each token exists
    - Remove unused tokens aggressively
    