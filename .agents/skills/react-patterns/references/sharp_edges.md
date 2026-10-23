# React Patterns - Sharp Edges

## React Useeffect Object Dep

### **Id**
react-useeffect-object-dep
### **Summary**
Object/array in useEffect deps causes infinite loop
### **Severity**
critical
### **Situation**
Passing object or array literal to useEffect dependency array
### **Why**
  Objects and arrays are compared by reference, not value.
  A new object literal {} is created every render, so the dependency
  is "always different", triggering the effect infinitely.
  
### **Solution**
  Options to fix:
  
  // WRONG - infinite loop
  useEffect(() => {
    fetchData(options)
  }, [{ page: 1, limit: 10 }])  // New object every render!
  
  // RIGHT - useMemo the object
  const options = useMemo(() => ({ page: 1, limit: 10 }), [])
  useEffect(() => {
    fetchData(options)
  }, [options])
  
  // RIGHT - use primitive deps
  useEffect(() => {
    fetchData({ page, limit })
  }, [page, limit])
  
  // RIGHT - stringify if needed (last resort)
  const optionsKey = JSON.stringify(options)
  useEffect(() => {
    fetchData(options)
  }, [optionsKey])
  
### **Symptoms**
  - Maximum update depth exceeded
  - Component keeps re-rendering
  - API called infinitely
  - Browser freezes/crashes
### **Detection Pattern**
useEffect\\([^)]+,\\s*\\[[^\\]]*\\{[^}]*\\}[^\\]]*\\]
### **Version Range**
>=16.8.0

## React Stale Closure

### **Id**
react-stale-closure
### **Summary**
Event handler or effect uses stale state/props
### **Severity**
high
### **Situation**
Callback references old value instead of current
### **Why**
  Closures capture values at creation time. If a callback is created
  once (empty deps) but references state, it will always see the
  initial state value, not updates.
  
### **Solution**
  // WRONG - stale closure
  const [count, setCount] = useState(0)
  useEffect(() => {
    const interval = setInterval(() => {
      console.log(count)  // Always 0!
      setCount(count + 1)  // Always sets to 1!
    }, 1000)
    return () => clearInterval(interval)
  }, [])  // Empty deps = created once
  
  // RIGHT - use functional update
  useEffect(() => {
    const interval = setInterval(() => {
      setCount(prev => prev + 1)  // Uses current value
    }, 1000)
    return () => clearInterval(interval)
  }, [])
  
  // RIGHT - use ref for read-only access
  const countRef = useRef(count)
  useEffect(() => {
    countRef.current = count
  }, [count])
  
  useEffect(() => {
    const interval = setInterval(() => {
      console.log(countRef.current)  // Current value
    }, 1000)
    return () => clearInterval(interval)
  }, [])
  
### **Symptoms**
  - Value doesn't update in callback
  - Console.log shows old value
  - Increment only works once
  - setTimeout/setInterval uses stale data
### **Detection Pattern**
useEffect\\([^)]+,\\s*\\[\\s*\\]\\)
### **Version Range**
>=16.8.0

## React Setstate In Render

### **Id**
react-setstate-in-render
### **Summary**
setState called during render causes infinite loop
### **Severity**
critical
### **Situation**
Calling setState unconditionally in component body
### **Why**
  setState triggers a re-render. If called during render without
  conditions, it creates an infinite loop: render → setState →
  render → setState → ...
  
### **Solution**
  // WRONG - infinite loop
  function Component({ data }) {
    const [processed, setProcessed] = useState(null)
    setProcessed(transform(data))  // Called every render!
    return <div>{processed}</div>
  }
  
  // RIGHT - use initial state function
  function Component({ data }) {
    const [processed] = useState(() => transform(data))
    return <div>{processed}</div>
  }
  
  // RIGHT - use useMemo for derived state
  function Component({ data }) {
    const processed = useMemo(() => transform(data), [data])
    return <div>{processed}</div>
  }
  
  // RIGHT - useEffect if truly needed
  function Component({ data }) {
    const [processed, setProcessed] = useState(null)
    useEffect(() => {
      setProcessed(transform(data))
    }, [data])
    return <div>{processed}</div>
  }
  
### **Symptoms**
  - Too many re-renders error
  - Maximum update depth exceeded
  - Component freezes
  - DevTools shows rapid re-renders
### **Detection Pattern**
function \\w+\\([^)]*\\)\\s*\\{[^}]*set\\w+\\([^)]+\\)[^}]*return
### **Version Range**
>=16.8.0

## React Missing Key Prop

### **Id**
react-missing-key-prop
### **Summary**
Missing or incorrect key in list causes bugs
### **Severity**
high
### **Situation**
Rendering arrays without proper key props
### **Why**
  React uses keys to track which items changed. Without keys (or with
  index as key for dynamic lists), React can't correctly match items,
  causing wrong items to update or state to attach to wrong elements.
  
### **Solution**
  // WRONG - no key
  items.map(item => <Item {...item} />)
  
  // WRONG - index as key for dynamic list
  items.map((item, index) => <Item key={index} {...item} />)
  
  // RIGHT - stable unique ID
  items.map(item => <Item key={item.id} {...item} />)
  
  // When items truly have no ID, generate one:
  const itemsWithIds = useMemo(
    () => items.map(item => ({ ...item, _id: crypto.randomUUID() })),
    [items]  // Only regenerate when items change
  )
  
  // Index is OK for static lists that never reorder:
  const staticItems = ['Home', 'About', 'Contact']
  staticItems.map((item, i) => <NavLink key={i}>{item}</NavLink>)
  
### **Symptoms**
  - Wrong item updates when list changes
  - Input values jump between items
  - Animations play on wrong elements
  - State attached to wrong component
### **Detection Pattern**
\\.map\\([^)]+=>\\s*<[A-Z](?![^>]*key=)
### **Version Range**
>=0.14.0

## React Useeffect Cleanup

### **Id**
react-useeffect-cleanup
### **Summary**
Missing cleanup causes memory leaks
### **Severity**
high
### **Situation**
useEffect subscribes/adds listeners without cleanup
### **Why**
  Without cleanup, subscriptions and listeners accumulate.
  When component unmounts, old listeners still fire, causing
  "setState on unmounted component" warnings and memory leaks.
  
### **Solution**
  // WRONG - no cleanup
  useEffect(() => {
    window.addEventListener('resize', handleResize)
    const subscription = api.subscribe(handleData)
  }, [])
  
  // RIGHT - cleanup function
  useEffect(() => {
    window.addEventListener('resize', handleResize)
    const subscription = api.subscribe(handleData)
  
    return () => {
      window.removeEventListener('resize', handleResize)
      subscription.unsubscribe()
    }
  }, [])
  
  // For async effects, use abort controller
  useEffect(() => {
    const controller = new AbortController()
  
    async function fetchData() {
      try {
        const res = await fetch(url, { signal: controller.signal })
        const data = await res.json()
        setData(data)
      } catch (e) {
        if (e.name !== 'AbortError') throw e
      }
    }
    fetchData()
  
    return () => controller.abort()
  }, [url])
  
### **Symptoms**
  - Can't perform state update on unmounted component
  - Memory usage grows over time
  - Event handlers fire after navigation
  - Subscriptions receive data after unmount
### **Detection Pattern**
useEffect\\([^)]+addEventListener|subscribe[^)]+(?!return)
### **Version Range**
>=16.8.0

## React Context Default Value

### **Id**
react-context-default-value
### **Summary**
Context default value used when Provider missing
### **Severity**
medium
### **Situation**
Using context but forgetting to wrap with Provider
### **Why**
  createContext default value is used when there's no Provider above
  in the tree. Components may silently use defaults instead of
  erroring, hiding the missing Provider bug.
  
### **Solution**
  // Default that will hide bugs
  const ThemeContext = createContext('light')
  
  // BETTER - default that will error
  const ThemeContext = createContext<Theme | null>(null)
  
  function useTheme() {
    const context = useContext(ThemeContext)
    if (context === null) {
      throw new Error('useTheme must be used within ThemeProvider')
    }
    return context
  }
  
  // BEST - create a custom provider hook
  const ThemeContext = createContext<ThemeContextType | undefined>(undefined)
  
  function ThemeProvider({ children }: { children: React.ReactNode }) {
    const [theme, setTheme] = useState<Theme>('light')
    return (
      <ThemeContext.Provider value={{ theme, setTheme }}>
        {children}
      </ThemeContext.Provider>
    )
  }
  
  function useTheme() {
    const context = useContext(ThemeContext)
    if (!context) {
      throw new Error('useTheme must be used within ThemeProvider')
    }
    return context
  }
  
### **Symptoms**
  - Context value is always default
  - setContext function is undefined
  - Component works alone, breaks in app
  - Silent failures instead of errors
### **Detection Pattern**
createContext\\([^)]*\\)
### **Version Range**
>=16.3.0

## React Usememo Reference

### **Id**
react-usememo-reference
### **Summary**
useMemo returning same reference when deps change
### **Severity**
medium
### **Situation**
useMemo doesn't recalculate when expected
### **Why**
  If useMemo's deps don't change according to reference equality,
  it returns the memoized value. Primitives compare by value,
  objects by reference. Stale deps mean stale results.
  
### **Solution**
  // WRONG - object dep always "same" because created outside
  const config = { page: 1 }  // Same reference every render
  const data = useMemo(() => process(config), [config])
  
  // RIGHT - primitive deps
  const data = useMemo(() => process({ page }), [page])
  
  // WRONG - missing dep
  const data = useMemo(() => items.filter(i => i.type === filter), [items])
  // filter is missing!
  
  // RIGHT - all deps included
  const data = useMemo(() => items.filter(i => i.type === filter), [items, filter])
  
  // Use ESLint rule to catch:
  // "react-hooks/exhaustive-deps": "warn"
  
### **Symptoms**
  - Memoized value is stale
  - Child components don't re-render
  - Computed value doesn't update
  - Filter/sort seems broken
### **Detection Pattern**
useMemo\\(
### **Version Range**
>=16.8.0

## React Forward Ref Missing

### **Id**
react-forward-ref-missing
### **Summary**
Ref not forwarded to DOM element in custom component
### **Severity**
medium
### **Situation**
Parent passes ref to custom component, but it's not received
### **Why**
  Regular function components don't receive refs as props.
  Without forwardRef, the ref prop is ignored, and parent's ref
  is always null.
  
### **Solution**
  // WRONG - ref is ignored
  function Input({ ref, ...props }) {
    return <input ref={ref} {...props} />  // ref is undefined!
  }
  
  // RIGHT - forwardRef
  const Input = forwardRef<HTMLInputElement, InputProps>((props, ref) => {
    return <input ref={ref} {...props} />
  })
  
  // RIGHT - React 19+ can use ref as prop
  function Input({ ref, ...props }: InputProps & { ref?: Ref<HTMLInputElement> }) {
    return <input ref={ref} {...props} />
  }
  
  // With useImperativeHandle for custom methods
  const Input = forwardRef<InputHandle, InputProps>((props, ref) => {
    const inputRef = useRef<HTMLInputElement>(null)
  
    useImperativeHandle(ref, () => ({
      focus: () => inputRef.current?.focus(),
      clear: () => { if (inputRef.current) inputRef.current.value = '' },
    }))
  
    return <input ref={inputRef} {...props} />
  })
  
### **Symptoms**
  - ref.current is always null
  - focus() doesn't work
  - Form libraries can't access input
  - Animation libraries can't measure element
### **Detection Pattern**
function \\w+\\([^)]*ref[^)]*\\)
### **Version Range**
>=16.3.0