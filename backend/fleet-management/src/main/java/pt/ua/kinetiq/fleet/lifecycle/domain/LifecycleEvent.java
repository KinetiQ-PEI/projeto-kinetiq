package pt.ua.kinetiq.fleet.lifecycle.domain;

/**
 * Acontecimentos que fazem uma bicicleta mudar de estado.
 * Pede-se a transição pelo evento ("o que aconteceu") e não pelo estado de destino:
 * assim a regra de negócio fica toda na máquina de estados.
 */
public enum LifecycleEvent {
    /** O rider desbloqueia a bicicleta e começa uma viagem. */
    START_TRIP,
    /** A viagem termina e a bicicleta volta a ficar disponível. */
    END_TRIP,
    /** Alerta crítico, ticket de manutenção ou decisão do operador: a bicicleta sai de circulação. */
    SEND_TO_MAINTENANCE,
    /** O técnico conclui a reparação. */
    COMPLETE_REPAIR,
    /** Fim de vida ou dano sem reparação: a bicicleta é retirada da frota (estado final). */
    RETIRE
}
