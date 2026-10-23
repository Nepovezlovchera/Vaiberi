# Tailwind Ui - Validations

## Dynamic Class Name Construction

### **Id**
tailwind-dynamic-class
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - className.*`[^`]*\$\{[^}]+\}[^`]*`
  - className.*\+.*(?:bg|text|border|p|m)-
### **Message**
Dynamic class construction may cause classes to be purged in production.
### **Fix Action**
Use a mapping object with complete class names or add to safelist
### **Applies To**
  - *.tsx
  - *.jsx

## Empty Content Array

### **Id**
tailwind-empty-content
### **Severity**
error
### **Type**
regex
### **Pattern**
  - content:\s*\[\s*\]
### **Message**
Empty content array means no Tailwind classes will be generated.
### **Fix Action**
Add file patterns to content array matching your source files
### **Applies To**
  - tailwind.config.js
  - tailwind.config.ts

## Deprecated Purge Option

### **Id**
tailwind-deprecated-purge
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - purge:\s*\[
  - mode:\s*["']jit["']
### **Message**
Using deprecated Tailwind v2 options. Use 'content' instead of 'purge'.
### **Fix Action**
Rename 'purge' to 'content' and remove 'mode: jit'
### **Applies To**
  - tailwind.config.js
  - tailwind.config.ts

## Excessive Important Modifier

### **Id**
tailwind-important-abuse
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - !\w+-\w+.*!\w+-\w+
  - className="[^"]*![^"]*![^"]*![^"]*"
### **Message**
Multiple !important modifiers suggest specificity issues.
### **Fix Action**
Fix the cascade or use tailwind-merge instead of !important
### **Applies To**
  - *.tsx
  - *.jsx

## Excessive Arbitrary Values

### **Id**
tailwind-arbitrary-overuse
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - \[\d+px\].*\[\d+px\].*\[\d+px\]
  - w-\[\d+px\]|h-\[\d+px\]|p-\[\d+px\]|m-\[\d+px\]
### **Message**
Many arbitrary values break design consistency. Consider extending theme.
### **Fix Action**
Use theme values or extend theme in tailwind.config.js
### **Applies To**
  - *.tsx
  - *.jsx

## Mixing Inline Styles with Tailwind

### **Id**
tailwind-inline-style-mix
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - className="[^"]+"\s*style=\{
  - style=\{[^}]+\}\s*className=
### **Message**
Mixing inline styles with Tailwind classes. Consider using Tailwind only.
### **Fix Action**
Use Tailwind utilities, arbitrary values, or CSS variables instead
### **Applies To**
  - *.tsx
  - *.jsx

## Dark Classes Without Config

### **Id**
tailwind-missing-dark-config
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - dark:
### **Message**
Using dark: classes. Ensure darkMode is configured in tailwind.config.js
### **Fix Action**
Add darkMode: 'class' or 'media' to tailwind.config.js
### **Applies To**
  - *.tsx
  - *.jsx
### **Check Config**
darkMode

## @apply Outside Layer

### **Id**
tailwind-apply-outside-layer
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - @apply(?![\s\S]{0,50}?@layer)
### **Message**
@apply used outside @layer directive may have unexpected specificity.
### **Fix Action**
Wrap custom CSS in @layer components or @layer utilities
### **Applies To**
  - *.css
  - globals.css