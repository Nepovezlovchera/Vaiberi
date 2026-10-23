# Tailwind Ui - Sharp Edges

## Tailwind Purge Dynamic

### **Id**
tailwind-purge-dynamic
### **Summary**
Dynamic class names get purged in production
### **Severity**
critical
### **Situation**
Building class names dynamically with template literals or concatenation
### **Why**
  Tailwind's purge scanner is static - it searches for complete class names
  in your source files. Dynamic strings like `bg-${color}-500` aren't found,
  so those classes are removed from the production build.
  
### **Solution**
  Always use complete class names that the scanner can find:
  
  // WRONG - will be purged
  const color = 'blue'
  <div className={`bg-${color}-500`}>
  
  // RIGHT - use mapping object
  const colorClasses = {
    blue: 'bg-blue-500',
    red: 'bg-red-500',
    green: 'bg-green-500',
  }
  <div className={colorClasses[color]}>
  
  // RIGHT - safelist in config
  // tailwind.config.js
  module.exports = {
    safelist: [
      'bg-blue-500',
      'bg-red-500',
      { pattern: /bg-(red|green|blue)-(100|500|900)/ },
    ],
  }
  
### **Symptoms**
  - Styles work in dev, missing in production
  - Dynamic colors/sizes don't apply after build
  - Classes work locally but not deployed
### **Detection Pattern**
className.*`[^`]*\\$\\{[^}]+\\}[^`]*`
### **Version Range**
>=2.0.0

## Tailwind Content Paths

### **Id**
tailwind-content-paths
### **Summary**
Missing content paths means no classes generated
### **Severity**
critical
### **Situation**
Classes don't work at all, CSS file is nearly empty
### **Why**
  Tailwind v3+ requires content paths to know which files to scan.
  If your paths don't match your actual file locations, no utilities
  are generated.
  
### **Solution**
  Ensure content paths match your project structure:
  
  // tailwind.config.js
  module.exports = {
    content: [
      './app/**/*.{js,ts,jsx,tsx,mdx}',      // Next.js App Router
      './pages/**/*.{js,ts,jsx,tsx,mdx}',    // Next.js Pages
      './components/**/*.{js,ts,jsx,tsx,mdx}',
      './src/**/*.{js,ts,jsx,tsx,mdx}',      // src directory
    ],
    // ...
  }
  
  // For monorepos, include package paths
  content: [
    './apps/web/**/*.{js,ts,jsx,tsx}',
    './packages/ui/**/*.{js,ts,jsx,tsx}',
  ]
  
### **Symptoms**
  - No Tailwind classes work at all
  - CSS output is tiny (just base styles)
  - Works in some files but not others
### **Detection Pattern**
content:\s*\[\]
### **Version Range**
>=3.0.0

## Tailwind Class Order

### **Id**
tailwind-class-order
### **Summary**
Class order doesn't determine specificity
### **Severity**
high
### **Situation**
Trying to override styles by putting class later in the string
### **Why**
  Unlike inline styles, Tailwind class order in your HTML doesn't matter.
  Specificity is determined by the order classes appear in the generated
  CSS file, which is based on Tailwind's layer order.
  
### **Solution**
  Use more specific variants or conditional logic:
  
  // WRONG - order doesn't help
  <div className="text-red-500 text-blue-500">  // Still red!
  
  // RIGHT - use conditional
  <div className={isBlue ? 'text-blue-500' : 'text-red-500'}>
  
  // RIGHT - use important modifier (sparingly)
  <div className="text-red-500 !text-blue-500">
  
  // RIGHT - use tailwind-merge library
  import { twMerge } from 'tailwind-merge'
  <div className={twMerge('text-red-500', 'text-blue-500')}>  // Blue wins
  
### **Symptoms**
  - Last class in string doesn't win
  - Override classes don't apply
  - Conditional styles behave unexpectedly
### **Detection Pattern**

### **Version Range**
>=1.0.0

## Tailwind Dark Mode Setup

### **Id**
tailwind-dark-mode-setup
### **Summary**
Dark mode not working without proper setup
### **Severity**
high
### **Situation**
Adding dark: classes but they never apply
### **Why**
  Tailwind's dark mode needs configuration. By default (media strategy),
  it follows system preference. Class strategy requires adding 'dark'
  class to html/body.
  
### **Solution**
  Choose and configure your strategy:
  
  // tailwind.config.js
  module.exports = {
    darkMode: 'class',  // or 'media' for system preference
    // ...
  }
  
  // For 'class' strategy, toggle on html element:
  // Light mode
  <html>
  
  // Dark mode
  <html class="dark">
  
  // In React/Next.js
  useEffect(() => {
    if (isDark) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }, [isDark])
  
### **Symptoms**
  
---
    ##### **Dark**
classes never apply
  
---
Dark mode works on some devices not others
  
---
Can't manually toggle dark mode
### **Detection Pattern**
dark:
### **Version Range**
>=2.0.0

## Tailwind Spacing Scale

### **Id**
tailwind-spacing-scale
### **Summary**
Not all numbers work for spacing utilities
### **Severity**
medium
### **Situation**
Using arbitrary spacing like p-7 or m-13 and it doesn't work
### **Why**
  Tailwind's default spacing scale doesn't include all numbers.
  It jumps: 0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 16...
  Values like p-13 or m-15 don't exist by default.
  
### **Solution**
  Use existing scale values, arbitrary values, or extend theme:
  
  // Use closest default value
  p-12 or p-14 instead of p-13
  
  // Use arbitrary value (avoid if possible)
  p-[52px] or p-[3.25rem]
  
  // Extend theme (best for consistency)
  // tailwind.config.js
  module.exports = {
    theme: {
      extend: {
        spacing: {
          '13': '3.25rem',
          '15': '3.75rem',
        }
      }
    }
  }
  
### **Symptoms**
  - Certain spacing values don't apply
  - Have to use arbitrary values frequently
  - Inconsistent spacing in design
### **Detection Pattern**
(?:p|m|gap|space)-(?:13|15|17|18|19)
### **Version Range**
>=1.0.0

## Tailwind Peer Sibling

### **Id**
tailwind-peer-sibling
### **Summary**
peer/group modifiers require specific DOM structure
### **Severity**
medium
### **Situation**
peer-checked or group-hover not working
### **Why**
  peer targets siblings that come AFTER the peer element.
  group requires the group class on a parent element.
  Wrong DOM structure = modifiers don't work.
  
### **Solution**
  Ensure correct DOM structure:
  
  // PEER - must come AFTER the peer element
  <div>
    <input type="checkbox" className="peer" />
    <span className="peer-checked:text-green-500">  {/* Sibling after */}
      This changes when checked
    </span>
  </div>
  
  // GROUP - must be inside group element
  <div className="group">  {/* Parent has group */}
    <span className="group-hover:text-blue-500">
      Hover the parent
    </span>
  </div>
  
  // WRONG - peer before target
  <span className="peer-checked:text-green-500">Won't work</span>
  <input type="checkbox" className="peer" />
  
### **Symptoms**
  - peer-* classes never apply
  - group-* hover doesn't work
  - Works in some components but not others
### **Detection Pattern**
peer-|group-
### **Version Range**
>=3.0.0

## Tailwind Jit Mode

### **Id**
tailwind-jit-mode
### **Summary**
JIT is now default - old workarounds may break
### **Severity**
medium
### **Situation**
Upgrading from Tailwind v2 to v3
### **Why**
  Tailwind v3 uses JIT (Just-in-Time) by default. Old workarounds like
  safelisting every color variant or using mode: 'jit' are no longer
  needed and may cause issues.
  
### **Solution**
  Clean up v2 workarounds:
  
  // REMOVE - no longer needed
  mode: 'jit',
  purge: [...],  // now called 'content'
  
  // KEEP - still valid
  content: [...],
  safelist: [...],  // still useful for dynamic classes
  
  // Check for deprecated options
  // v2 → v3 changes:
  // purge → content
  // mode: 'jit' → removed (default now)
  // variants → removed (all variants available)
  
### **Symptoms**
  - Config warnings on v3
  - Unexpected behavior after upgrade
  - Variants not working as expected
### **Detection Pattern**
mode:\s*['"]jit['"]|purge:
### **Version Range**
>=3.0.0

## Tailwind Layer Order

### **Id**
tailwind-layer-order
### **Summary**
Custom CSS in wrong layer gets unexpected specificity
### **Severity**
medium
### **Situation**
Custom CSS not overriding or being overridden unexpectedly
### **Why**
  Tailwind has three layers: base, components, utilities.
  Utilities have highest specificity. Custom CSS outside @layer
  may have unexpected precedence.
  
### **Solution**
  Always use @layer for custom CSS:
  
  /* globals.css */
  
  @tailwind base;
  @tailwind components;
  @tailwind utilities;
  
  /* Custom base styles */
  @layer base {
    html { @apply scroll-smooth; }
  }
  
  /* Custom components */
  @layer components {
    .btn { @apply px-4 py-2 rounded; }
  }
  
  /* Custom utilities */
  @layer utilities {
    .text-balance { text-wrap: balance; }
  }
  
  /* AVOID - outside layers, unpredictable specificity */
  .my-class { color: red; }
  
### **Symptoms**
  - Custom styles override utilities unexpectedly
  - Utilities don't override custom styles
  - Specificity conflicts
### **Detection Pattern**
@apply(?![\\s\\S]*?@layer)
### **Version Range**
>=2.0.0