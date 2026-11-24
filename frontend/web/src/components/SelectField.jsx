import { useId } from 'react'
import './SelectField.css'

// Labelled pill select. options: [{ value, label }]
export default function SelectField({ label, value, options, onChange, minWidth = 140 }) {
  const id = useId()
  return (
    <div className="select-field">
      <label htmlFor={id} className="select-field__label">
        {label}
      </label>
      <select
        id={id}
        className="select-field__control"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        style={{ minWidth }}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  )
}
