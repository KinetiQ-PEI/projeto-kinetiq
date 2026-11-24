# KinetiQ · Web app (backoffice do operador e do técnico)

Frontend web do KinetiQ, feito a partir das mockups da equipa (`kinetiq-dashboard-mockups-a11y`).
React 19 + Vite 8 + React Router 7, em JavaScript. Ícones: `lucide-react`.

## Correr

Requisitos: Node.js 20.19+ ou 22.12+.

```bash
cd frontend/web
npm install
npm run dev        # http://localhost:5173
```

| Comando | Para quê |
|---|---|
| `npm run dev` | Servidor de desenvolvimento, recarrega ao gravar |
| `npm run build` | Gera a versão final em `dist/` |
| `npm run preview` | Serve o `dist/` para testar a versão final |
| `npm run lint` | Verifica o código (oxlint) |

## Dados falsos vs. API real

Enquanto o backend não existe, todas as páginas usam dados falsos. A troca faz-se num só sítio:

```
página  →  src/services/*Service.js  →  dados falsos (src/data/mock/)   se VITE_USE_MOCKS=true
                                     →  API Gateway (fetch)              se VITE_USE_MOCKS=false
```

- As páginas **nunca** importam `src/data/mock/` diretamente: pedem sempre os dados a `src/services/`.
- Os dados falsos seguem os esquemas de `docs/schemas/` (ex.: os alertas têm a forma do `FleetAlertEvent`).
- O campo `details` dos alertas aparece como título da linha, por isso deve ser curto (ex.: `Low battery (12%)`): a confirmar com o backend.
- Para mudar, copiar `.env.example` para `.env` e alterar `VITE_USE_MOCKS` / `VITE_API_BASE_URL`.

### Endpoints que o frontend espera (a confirmar com o backend)

| Página | Endpoint | Dados |
|---|---|---|
| Overview | `GET /fleet/summary` | Nome da frota, bicicletas registadas, contagem por estado |
| Overview | `GET /fleet/kpis` | Viagens ativas, duração média, CO₂ |
| Overview | `GET /fleet/usage/hourly` | Bicicletas em uso por hora |
| Overview | `GET /alerts?status=open` | Alertas abertos (`FleetAlertEvent`) |
| Overview | `GET /zones`, `GET /zones/stats` | Zonas e bicicletas por zona e estado |
| Overview | `POST /alerts/{alertId}/acknowledge` | Marcar um alerta como visto (fica no histórico) |
| Overview | `POST /maintenance-tickets` | Criar ticket a partir de um alerta (`alertId`, `bikeId`, `reason`); a bicicleta passa a `IN_MAINTENANCE` |

### Papéis

O utilizador está simulado em `src/services/session.js` (Fleet Operator) até o Keycloak estar ligado.
O que cada papel pode fazer está em `src/data/permissions.js` (RNF08, a confirmar).

## Estrutura

```
src/
├── main.jsx, App.jsx     arranque e rotas (uma por mockup)
├── layouts/              barra de topo e navegação
├── pages/                uma página por rota
├── components/           componentes reutilizáveis (Card, StatCard, SeverityBadge...)
│   └── overview/         secções do Overview
├── services/             origem dos dados (mocks ou API)
├── hooks/                useAsync (loading / erro / recarregar)
├── data/                 constantes (estados, alertas, menu) e mock/ (dados falsos)
├── utils/                formatação (percentagens, "há X min")
└── styles/               tokens (cores, fontes) e estilos globais
```

## Estado das páginas

| Rota | Página | Estado |
|---|---|---|
| `/` | Overview | ✅ Igual à mockup, com dados falsos |
| `/live-map` | Live map | Por fazer (já recebe `?bike=`) |
| `/bikes`, `/bikes/:bikeId` | Bikes | Por fazer (já recebe o filtro `?state=` e o ID) |
| `/maintenance` | Maintenance | Por fazer |
| `/reports` | Reports | Por fazer. **Deve mostrar o CO₂ poupado** (RF13, RF18), que saiu do Overview |
| `/users-modules` | Users & modules | Por fazer |

## Acessibilidade

As mockups já tinham correções WCAG 2.2 AA (`a11y.css`). Foram mantidas na conversão, por exemplo:
foco visível, link "Skip to content", contraste das bordas dos campos e dos cabeçalhos das tabelas,
alvos de pelo menos 24×24 px, estados nunca indicados só por cor, e gráficos com uma tabela
equivalente para leitores de ecrã (RNF06).
