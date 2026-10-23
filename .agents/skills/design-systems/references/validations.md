# Design Systems - Validations

## Hardcoded Color Values

### **Id**
hardcoded-colors
### **Description**
Colors should use design tokens, not hardcoded hex/rgb values
### **Pattern**
(#[0-9a-fA-F]{3,8}|rgb\(|rgba\(|hsl\(|hsla\()
### **File Glob**
**/*.{css,scss,less,tsx,jsx,ts,js}
### **Match**
present
### **Exclude Pattern**
(var\(--|theme\.|tokens\.|colors\.)
### **Message**
Hardcoded color found. Use design token instead: var(--color-*)
### **Severity**
warning
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - background: #3B82F6;
    - color: rgb(59, 130, 246);
    - border-color: rgba(0, 0, 0, 0.1);
    - backgroundColor: '#2563EB'
    - fill: hsl(220, 90%, 56%);
  #### **Should Not Match**
    - background: var(--color-primary);
    - color: theme.colors.primary
    - backgroundColor: colors.blue[500]
    - /* #3B82F6 is primary blue */

## Hardcoded Spacing Values

### **Id**
hardcoded-spacing
### **Description**
Spacing should use design tokens, not arbitrary pixel values
### **Pattern**
(?<!line-height:\s*)(\d+)px
### **File Glob**
**/*.{css,scss,less}
### **Match**
present
### **Exclude Pattern**
(var\(--|spacing\.|space\.)
### **Message**
Hardcoded spacing found. Use spacing token: var(--spacing-*)
### **Severity**
info
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - padding: 16px;
    - margin: 24px 12px;
    - gap: 8px;
    - width: 200px;
  #### **Should Not Match**
    - padding: var(--spacing-md);
    - margin: var(--spacing-lg);
    - line-height: 24px;
    - /* 16px is our base unit */

## Missing Component Documentation

### **Id**
missing-component-docs
### **Description**
Components should have JSDoc documentation
### **Pattern**
export\s+(const|function)\s+[A-Z][a-zA-Z]+\s*[=\(]
### **File Glob**
**/components/**/*.{tsx,jsx}
### **Match**
present
### **Context Pattern**
/\*\*[\s\S]*?\*/
### **Message**
Component missing JSDoc documentation. Add description, props, and example.
### **Severity**
warning
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - export const Button = (
    - export function Card({
    - export const TextField = forwardRef(
  #### **Should Not Match**
    - const helper = (
    - function internalUtil(

## Missing Display Name for forwardRef

### **Id**
missing-display-name
### **Description**
forwardRef components need displayName for debugging
### **Pattern**
forwardRef\s*<
### **File Glob**
**/*.{tsx,jsx}
### **Match**
present
### **Context Pattern**
\.displayName\s*=
### **Context Lines After**

### **Message**
forwardRef component missing displayName. Add: Component.displayName = 'Component'
### **Severity**
warning
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - forwardRef<HTMLButtonElement, ButtonProps>
    - forwardRef<HTMLInputElement>((props, ref)

## Clickable Div Anti-pattern

### **Id**
div-onclick-antipattern
### **Description**
Clickable elements should be buttons, not divs
### **Pattern**
<div[^>]*onClick
### **File Glob**
**/*.{tsx,jsx}
### **Match**
present
### **Message**
Clickable div found. Use <button> or add role='button', tabIndex, keyboard handlers.
### **Severity**
error
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - <div onClick={handleClick}>
    - <div className='btn' onClick={click}>
  #### **Should Not Match**
    - <button onClick={handleClick}>
    - <div role='button' tabIndex={0} onClick={handleClick}>

## Missing Focus Visible Styles

### **Id**
missing-focus-styles
### **Description**
Interactive elements need visible focus styles for keyboard users
### **Pattern**
focus:
### **File Glob**
**/*.{tsx,jsx,css,scss}
### **Match**
absent
### **Context Pattern**
(button|input|select|a|\[role)
### **Message**
Focus styles may be missing. Add focus-visible ring for keyboard accessibility.
### **Severity**
warning
### **Autofix**

### **Test Cases**
  #### **Should Not Match**
    - focus:ring-2 focus:ring-offset-2
    - focus-visible:outline
    - &:focus-visible { outline: 2px solid

## CSS Important Override

### **Id**
important-override
### **Description**
Avoid !important - indicates specificity problems
### **Pattern**
!important
### **File Glob**
**/*.{css,scss,less}
### **Match**
present
### **Message**
!important found. Refactor to avoid specificity issues.
### **Severity**
warning
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - color: red !important;
    - display: none !important
  #### **Should Not Match**
    - /* sometimes !important is needed */

## Inconsistent Token Naming

### **Id**
inconsistent-token-naming
### **Description**
Token names should follow category-property-variant pattern
### **Pattern**
--[a-zA-Z]+[A-Z]|--[a-z]+_[a-z]+|--[A-Z]
### **File Glob**
**/*.{css,scss,ts,js}
### **Match**
present
### **Message**
Inconsistent token naming. Use kebab-case: --category-property-variant
### **Severity**
warning
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - --colorPrimary
    - --color_primary
    - --ColorPrimary
    - --textColor
  #### **Should Not Match**
    - --color-primary
    - --color-text-muted
    - --spacing-md

## Missing Accessible Name

### **Id**
missing-aria-label
### **Description**
Icon-only buttons need aria-label
### **Pattern**
<(button|Button)[^>]*>[^<]*<(Icon|svg|img)[^>]*/?>[^<]*</(button|Button)>
### **File Glob**
**/*.{tsx,jsx}
### **Match**
present
### **Context Pattern**
aria-label
### **Message**
Icon-only button missing aria-label. Add aria-label for screen readers.
### **Severity**
error
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - <button><Icon /></button>
    - <Button><CloseIcon /></Button>
  #### **Should Not Match**
    - <button aria-label='Close'><Icon /></button>
    - <button><Icon /> Close</button>

## Missing Loading State in Async Buttons

### **Id**
missing-loading-state
### **Description**
Buttons triggering async actions should have loading state
### **Pattern**
(onClick|onSubmit)[^}]*await
### **File Glob**
**/*.{tsx,jsx}
### **Match**
present
### **Context Pattern**
(loading|isLoading|pending)
### **Message**
Async action without loading state. Add loading prop to prevent double-clicks.
### **Severity**
info
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - onClick={async () => { await submit(); }}
    - onSubmit={async (e) => { await handleSubmit(); }}

## Missing Error Boundary

### **Id**
missing-error-boundary
### **Description**
Component sections should have error boundaries
### **Pattern**
export\s+(default\s+)?function\s+[A-Z][a-zA-Z]*Page
### **File Glob**
**/pages/**/*.{tsx,jsx}
### **Match**
present
### **Context Pattern**
ErrorBoundary
### **Message**
Page component may need error boundary for resilience.
### **Severity**
info
### **Autofix**


## Primitive Token Used Directly

### **Id**
token-primitive-in-component
### **Description**
Components should use semantic tokens, not primitives
### **Pattern**
(blue|red|green|gray|slate|zinc)-(50|100|200|300|400|500|600|700|800|900)
### **File Glob**
**/*.{tsx,jsx,css,scss}
### **Match**
present
### **Message**
Primitive color token used directly. Use semantic token (color-primary, color-text-muted).
### **Severity**
warning
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - text-blue-500
    - bg-gray-100
    - border-red-600
  #### **Should Not Match**
    - text-primary
    - bg-background
    - border-error

## Magic Z-Index Values

### **Id**
magic-number-z-index
### **Description**
Z-index should use defined scale, not magic numbers
### **Pattern**
z-index:\s*\d{2,}
### **File Glob**
**/*.{css,scss}
### **Match**
present
### **Message**
Magic z-index found. Use z-index scale: var(--z-dropdown), var(--z-modal), etc.
### **Severity**
info
### **Autofix**

### **Test Cases**
  #### **Should Match**
    - z-index: 9999;
    - z-index: 100;
    - z-index: 50;
  #### **Should Not Match**
    - z-index: var(--z-modal);
    - z-index: 1;
    - z-index: -1;