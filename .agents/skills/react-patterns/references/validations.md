# React Patterns - Validations

## Object Literal in Dependency Array

### **Id**
react-object-in-deps
### **Severity**
error
### **Type**
regex
### **Pattern**
  - useEffect\([^)]+,\s*\[[^\]]*\{[^}]*\}[^\]]*\]
  - useMemo\([^)]+,\s*\[[^\]]*\{[^}]*\}[^\]]*\]
  - useCallback\([^)]+,\s*\[[^\]]*\{[^}]*\}[^\]]*\]
### **Message**
Object literal in dependency array causes infinite loops. Memoize the object or use primitives.
### **Fix Action**
Use useMemo for the object or extract primitive values as deps
### **Applies To**
  - *.tsx
  - *.jsx

## Empty Deps with State Reference

### **Id**
react-empty-deps-with-state
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - useEffect\(\(\)\s*=>\s*\{[^}]*\b(count|value|data|items|state)\b[^}]*\},\s*\[\s*\]\)
### **Message**
Empty dependency array but referencing state may cause stale closure.
### **Fix Action**
Add state to deps or use functional update (setState(prev => ...))
### **Applies To**
  - *.tsx
  - *.jsx

## Missing Key in Map

### **Id**
react-missing-key
### **Severity**
error
### **Type**
regex
### **Pattern**
  - \.map\([^)]+\)\s*=>\s*\(\s*<[A-Z][^>]*(?!key=)[^>]*>
  - \.map\([^)]+\)\s*=>\s*<[A-Z][^>]*(?!key=)[^>]*>
### **Message**
Missing key prop in mapped elements. Add unique key for proper reconciliation.
### **Fix Action**
Add key={item.id} or another unique identifier
### **Applies To**
  - *.tsx
  - *.jsx

## Array Index as Key

### **Id**
react-index-as-key
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - \.map\(\s*\([^,]+,\s*(\w+)\)\s*=>[^)]*key=\{\s*\1\s*\}
  - \.map\(\s*\([^,]+,\s*index\)\s*=>[^)]*key=\{index\}
### **Message**
Using array index as key can cause bugs when list items change order.
### **Fix Action**
Use unique item ID instead: key={item.id}
### **Applies To**
  - *.tsx
  - *.jsx

## setState Called During Render

### **Id**
react-setstate-in-render
### **Severity**
error
### **Type**
regex
### **Pattern**
  - function \w+\([^)]*\)\s*\{[^}]*set\w+\([^)]+\)[^}]*return\s*[(<]
### **Message**
setState called during render causes infinite loop. Move to useEffect or event handler.
### **Fix Action**
Use useEffect for side effects or useMemo for derived state
### **Applies To**
  - *.tsx
  - *.jsx

## useEffect Without Cleanup

### **Id**
react-missing-cleanup
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - useEffect\(\(\)\s*=>\s*\{[^}]*addEventListener[^}]*\}[^}]*(?!return)
  - useEffect\(\(\)\s*=>\s*\{[^}]*subscribe[^}]*\}[^}]*(?!return)
  - useEffect\(\(\)\s*=>\s*\{[^}]*setInterval[^}]*\}[^}]*(?!return)
### **Message**
useEffect with subscription/listener but no cleanup. Add return function to clean up.
### **Fix Action**
Add cleanup: return () => { removeEventListener/unsubscribe/clearInterval }
### **Applies To**
  - *.tsx
  - *.jsx

## useContext Without Null Check

### **Id**
react-context-no-check
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - const \w+ = useContext\(\w+\)(?!\s*\n[^}]*(?:if|throw|\?\.))
### **Message**
useContext without null check. Context may be undefined if Provider is missing.
### **Fix Action**
Add null check and throw helpful error, or create a custom hook
### **Applies To**
  - *.tsx
  - *.jsx

## Inline Function in JSX Prop

### **Id**
react-inline-function-prop
### **Severity**
warning
### **Type**
regex
### **Pattern**
  - onClick=\{\(\)\s*=>
  - onChange=\{\([^)]*\)\s*=>
### **Message**
Inline function creates new reference every render. May cause unnecessary re-renders.
### **Fix Action**
Extract to useCallback if child component is memoized
### **Applies To**
  - *.tsx
  - *.jsx

## Async Function as useEffect Callback

### **Id**
react-async-useeffect
### **Severity**
error
### **Type**
regex
### **Pattern**
  - useEffect\(async\s*\(
  - useEffect\(\s*async\s*\(\)\s*=>
### **Message**
useEffect callback cannot be async directly. Define async function inside and call it.
### **Fix Action**
useEffect(() => { async function fetch() {...} fetch() }, [deps])
### **Applies To**
  - *.tsx
  - *.jsx