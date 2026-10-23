# Tailwind CSS UI

## Patterns


---
  #### **Name**
Responsive Mobile-First
  #### **Description**
Build layouts starting from mobile, adding breakpoint modifiers
  #### **When**
Creating any responsive layout
  #### **Example**
    {/* Mobile-first: stack by default, row on md+ */}
    <div className="flex flex-col md:flex-row gap-4">
      <div className="w-full md:w-1/3">Sidebar</div>
      <div className="w-full md:w-2/3">Content</div>
    </div>
    
    {/* Text sizing responsive */}
    <h1 className="text-2xl md:text-4xl lg:text-6xl font-bold">
      Responsive Heading
    </h1>
    

---
  #### **Name**
Dark Mode with Class Strategy
  #### **Description**
Implement dark mode using the class strategy for manual control
  #### **When**
Building apps with dark mode toggle
  #### **Example**
    // tailwind.config.js
    module.exports = {
      darkMode: 'class',
      // ...
    }
    
    // Component
    <div className="bg-white dark:bg-gray-900 text-gray-900 dark:text-white">
      <button className="bg-blue-500 dark:bg-blue-600 hover:bg-blue-600 dark:hover:bg-blue-700">
        Click me
      </button>
    </div>
    
    // Toggle in React
    document.documentElement.classList.toggle('dark')
    

---
  #### **Name**
Component Extraction with @apply
  #### **Description**
Extract repeated utility patterns into component classes
  #### **When**
Same utility combination used 3+ times
  #### **Example**
    /* globals.css */
    @layer components {
      .btn {
        @apply px-4 py-2 rounded-lg font-medium transition-colors;
      }
      .btn-primary {
        @apply btn bg-blue-500 text-white hover:bg-blue-600;
      }
      .btn-secondary {
        @apply btn bg-gray-200 text-gray-900 hover:bg-gray-300;
      }
    }
    
    /* Or use React components (preferred) */
    function Button({ variant = 'primary', children, ...props }) {
      const styles = {
        primary: 'bg-blue-500 text-white hover:bg-blue-600',
        secondary: 'bg-gray-200 text-gray-900 hover:bg-gray-300',
      }
      return (
        <button
          className={`px-4 py-2 rounded-lg font-medium transition-colors ${styles[variant]}`}
          {...props}
        >
          {children}
        </button>
      )
    }
    

---
  #### **Name**
Container with Centered Content
  #### **Description**
Standard container pattern for page content
  #### **When**
Creating page layouts with max-width content
  #### **Example**
    {/* Basic centered container */}
    <div className="container mx-auto px-4">
      Content here
    </div>
    
    {/* With max-width variants */}
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      Content here
    </div>
    
    {/* Full-width background, contained content */}
    <section className="bg-gray-100">
      <div className="max-w-7xl mx-auto px-4 py-12">
        Content here
      </div>
    </section>
    

---
  #### **Name**
Flexbox Card Grid
  #### **Description**
Responsive card grid using flexbox or grid
  #### **When**
Displaying collections of cards
  #### **Example**
    {/* CSS Grid (preferred for equal columns) */}
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      {items.map(item => (
        <div key={item.id} className="bg-white rounded-lg shadow p-6">
          {item.content}
        </div>
      ))}
    </div>
    
    {/* Flexbox (for variable width items) */}
    <div className="flex flex-wrap -mx-2">
      {items.map(item => (
        <div key={item.id} className="w-full md:w-1/2 lg:w-1/3 px-2 mb-4">
          <div className="bg-white rounded-lg shadow p-6 h-full">
            {item.content}
          </div>
        </div>
      ))}
    </div>
    

---
  #### **Name**
Focus and Hover States
  #### **Description**
Proper interactive states for accessibility
  #### **When**
Building interactive elements
  #### **Example**
    {/* Button with all states */}
    <button className="
      bg-blue-500 text-white px-4 py-2 rounded
      hover:bg-blue-600
      focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2
      active:bg-blue-700
      disabled:opacity-50 disabled:cursor-not-allowed
      transition-colors
    ">
      Click me
    </button>
    
    {/* Link with focus visible (keyboard only) */}
    <a href="#" className="
      text-blue-500 underline
      hover:text-blue-700
      focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500
    ">
      Learn more
    </a>
    

## Anti-Patterns


---
  #### **Name**
Arbitrary Values Overuse
  #### **Description**
Using arbitrary values like w-[347px] instead of theme values
  #### **Why**
Breaks design consistency, harder to maintain, larger CSS output
  #### **Instead**
Use theme values (w-80, w-96) or extend theme in config

---
  #### **Name**
Important Modifier Abuse
  #### **Description**
Using !important (!mt-4) to override specificity issues
  #### **Why**
Creates specificity wars, makes debugging harder
  #### **Instead**
Fix the cascade, use more specific selectors, or restructure

---
  #### **Name**
Inline Style Mixing
  #### **Description**
Mixing Tailwind classes with inline styles
  #### **Why**
Two systems to maintain, inconsistent, can't use theme values
  #### **Instead**
Extend Tailwind config or use CSS variables

---
  #### **Name**
Deep Nesting in @apply
  #### **Description**
Creating deeply nested @apply chains
  #### **Why**
Hard to debug, obscures actual styles, defeats utility purpose
  #### **Instead**
Use React components for abstraction, keep @apply simple

---
  #### **Name**
Ignoring Mobile-First
  #### **Description**
Starting with desktop styles, using sm:hidden for mobile
  #### **Why**
Larger CSS, harder to maintain, mobile often afterthought
  #### **Instead**
Start mobile, add md:, lg: for larger screens