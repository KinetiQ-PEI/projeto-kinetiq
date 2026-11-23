import './SegmentedControl.css'

// Group of toggle buttons where exactly one is selected (aria-pressed).
// options: [{ value, label }]
export default function SegmentedControl({ label, value, options, onChange, size = 'md' }) {
  return (
    <div role="group" aria-label={label} className={`segmented segmented--${size}`}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          className="segmented__option"
          aria-pressed={option.value === value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
