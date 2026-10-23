# React Patterns

## Patterns


---
  #### **Name**
Custom Hook Extraction
  #### **Description**
Extract reusable logic into custom hooks
  #### **When**
Same stateful logic used in multiple components
  #### **Example**
    // hooks/useLocalStorage.ts
    function useLocalStorage<T>(key: string, initialValue: T) {
      const [storedValue, setStoredValue] = useState<T>(() => {
        if (typeof window === 'undefined') return initialValue
        try {
          const item = window.localStorage.getItem(key)
          return item ? JSON.parse(item) : initialValue
        } catch {
          return initialValue
        }
      })
    
      const setValue = useCallback((value: T | ((val: T) => T)) => {
        try {
          const valueToStore = value instanceof Function ? value(storedValue) : value
          setStoredValue(valueToStore)
          window.localStorage.setItem(key, JSON.stringify(valueToStore))
        } catch (error) {
          console.error(error)
        }
      }, [key, storedValue])
    
      return [storedValue, setValue] as const
    }
    
    // Usage
    const [theme, setTheme] = useLocalStorage('theme', 'light')
    

---
  #### **Name**
Compound Components
  #### **Description**
Related components that share implicit state
  #### **When**
Building complex UI components with multiple parts
  #### **Example**
    // Compound component pattern
    const TabsContext = createContext<TabsContextType | null>(null)
    
    function Tabs({ children, defaultValue }: TabsProps) {
      const [activeTab, setActiveTab] = useState(defaultValue)
      return (
        <TabsContext.Provider value={{ activeTab, setActiveTab }}>
          <div className="tabs">{children}</div>
        </TabsContext.Provider>
      )
    }
    
    function TabList({ children }: { children: React.ReactNode }) {
      return <div className="tab-list" role="tablist">{children}</div>
    }
    
    function Tab({ value, children }: TabProps) {
      const { activeTab, setActiveTab } = useContext(TabsContext)!
      return (
        <button
          role="tab"
          aria-selected={activeTab === value}
          onClick={() => setActiveTab(value)}
        >
          {children}
        </button>
      )
    }
    
    function TabPanel({ value, children }: TabPanelProps) {
      const { activeTab } = useContext(TabsContext)!
      if (activeTab !== value) return null
      return <div role="tabpanel">{children}</div>
    }
    
    // Attach sub-components
    Tabs.List = TabList
    Tabs.Tab = Tab
    Tabs.Panel = TabPanel
    
    // Usage
    <Tabs defaultValue="tab1">
      <Tabs.List>
        <Tabs.Tab value="tab1">First</Tabs.Tab>
        <Tabs.Tab value="tab2">Second</Tabs.Tab>
      </Tabs.List>
      <Tabs.Panel value="tab1">First content</Tabs.Panel>
      <Tabs.Panel value="tab2">Second content</Tabs.Panel>
    </Tabs>
    

---
  #### **Name**
Render Props / Children as Function
  #### **Description**
Pass render logic as a prop for flexible composition
  #### **When**
Component needs to share data but caller controls rendering
  #### **Example**
    // Render prop pattern
    interface MouseTrackerProps {
      children: (position: { x: number; y: number }) => React.ReactNode
    }
    
    function MouseTracker({ children }: MouseTrackerProps) {
      const [position, setPosition] = useState({ x: 0, y: 0 })
    
      useEffect(() => {
        const handleMove = (e: MouseEvent) => {
          setPosition({ x: e.clientX, y: e.clientY })
        }
        window.addEventListener('mousemove', handleMove)
        return () => window.removeEventListener('mousemove', handleMove)
      }, [])
    
      return <>{children(position)}</>
    }
    
    // Usage
    <MouseTracker>
      {({ x, y }) => (
        <div>Mouse is at ({x}, {y})</div>
      )}
    </MouseTracker>
    

---
  #### **Name**
Controlled vs Uncontrolled
  #### **Description**
Support both controlled and uncontrolled usage
  #### **When**
Building reusable form components
  #### **Example**
    interface InputProps {
      value?: string           // Controlled
      defaultValue?: string    // Uncontrolled
      onChange?: (value: string) => void
    }
    
    function Input({ value, defaultValue, onChange }: InputProps) {
      // Internal state for uncontrolled mode
      const [internalValue, setInternalValue] = useState(defaultValue ?? '')
    
      // Use provided value if controlled, internal if not
      const isControlled = value !== undefined
      const inputValue = isControlled ? value : internalValue
    
      const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const newValue = e.target.value
        if (!isControlled) {
          setInternalValue(newValue)
        }
        onChange?.(newValue)
      }
    
      return <input value={inputValue} onChange={handleChange} />
    }
    
    // Controlled usage
    const [name, setName] = useState('')
    <Input value={name} onChange={setName} />
    
    // Uncontrolled usage
    <Input defaultValue="initial" onChange={console.log} />
    

---
  #### **Name**
Optimistic Updates
  #### **Description**
Update UI immediately, roll back on error
  #### **When**
User actions that call APIs (likes, saves, deletes)
  #### **Example**
    function LikeButton({ postId, initialLiked }: LikeButtonProps) {
      const [liked, setLiked] = useState(initialLiked)
      const [isPending, startTransition] = useTransition()
    
      const toggleLike = async () => {
        const previousLiked = liked
    
        // Optimistic update
        setLiked(!liked)
    
        try {
          await fetch(`/api/posts/${postId}/like`, {
            method: liked ? 'DELETE' : 'POST',
          })
        } catch (error) {
          // Roll back on error
          setLiked(previousLiked)
          toast.error('Failed to update')
        }
      }
    
      return (
        <button onClick={toggleLike} disabled={isPending}>
          {liked ? '❤️' : '🤍'}
        </button>
      )
    }
    

---
  #### **Name**
Derived State from Props
  #### **Description**
Calculate values from props/state without useEffect
  #### **When**
You need computed values based on other state
  #### **Example**
    // WRONG - useEffect for derived state
    function FilteredList({ items, filter }: Props) {
      const [filteredItems, setFilteredItems] = useState(items)
    
      useEffect(() => {
        setFilteredItems(items.filter(item => item.includes(filter)))
      }, [items, filter])
    
      return <List items={filteredItems} />
    }
    
    // RIGHT - compute during render
    function FilteredList({ items, filter }: Props) {
      const filteredItems = useMemo(
        () => items.filter(item => item.includes(filter)),
        [items, filter]
      )
    
      return <List items={filteredItems} />
    }
    
    // ALSO RIGHT - for simple calculations, skip useMemo
    function FilteredList({ items, filter }: Props) {
      const filteredItems = items.filter(item => item.includes(filter))
      return <List items={filteredItems} />
    }
    

## Anti-Patterns


---
  #### **Name**
useEffect for Derived State
  #### **Description**
Using useEffect to compute values from props/state
  #### **Why**
Causes extra renders, race conditions, and complexity
  #### **Instead**
Calculate during render, use useMemo if expensive

---
  #### **Name**
Prop Drilling
  #### **Description**
Passing props through many layers of components
  #### **Why**
Makes refactoring hard, components tightly coupled
  #### **Instead**
Use Context, composition (children), or state management

---
  #### **Name**
Giant Components
  #### **Description**
Components with hundreds of lines and many responsibilities
  #### **Why**
Hard to test, maintain, and reuse
  #### **Instead**
Extract smaller components, custom hooks for logic

---
  #### **Name**
Premature Memoization
  #### **Description**
Using useMemo/useCallback everywhere "just in case"
  #### **Why**
Adds complexity, memory overhead, often slower than re-computing
  #### **Instead**
Profile first, memoize only proven bottlenecks

---
  #### **Name**
State for Everything
  #### **Description**
Using useState for values that could be derived or refs
  #### **Why**
Unnecessary re-renders, complex state synchronization
  #### **Instead**
Derive values, use useRef for non-rendering values