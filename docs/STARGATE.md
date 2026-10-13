# Stargate (Galaxyfun)

Portal para viajar entre dos puntos (incluso entre mundos, p. ej. Tierra <-> Luna). Se arma con **15 Stargate Ring** y
**1 Stargate Controller**.

## Como se arma

El arco es vertical, de 7 de ancho y 7 de alto. `C` = controlador, `R` = Stargate Ring, `.` = interior (libre).
Se acepta orientado **a lo largo del eje X o del eje Z** (el juego prueba los dos; ya no hace falta adivinar).

```
 altura 6        R R R
 altura 5      R . . . R
 altura 4    R . . . . . R
 altura 3    R . . . . . R
 altura 2    R . . . . . R
 altura 1      R . . . R
 altura 0        R C R          <- el controlador va en el CENTRO de la base
```

- El interior (21 bloques) debe estar **libre**: si hay una maquina de Slimefun dentro, no enciende y te dice cual.
- Si algo falta, al hacer click derecho al controlador el chat dice **cuantos anillos faltan, donde (coordenadas) y que hay
  en su lugar**, y se marcan con particulas.

## Como se usa

1. Click derecho al controlador: **enciende** el portal.
2. Click derecho de nuevo: menu (el Stargate encendido abre el menu; apagado, lo enciende).
   - **Estado**: estructura, portal, destino y si el destino esta encendido y apunta de vuelta.
   - **Direccion**: click izquierdo la manda al chat (copiable); click derecho le pones un **nombre** facil.
   - **Elegir destino**: click izquierdo = lista de Stargates ordenada por cercania; click derecho = escribir direccion o nombre.
   - **Enlazar retorno**: hace que el destino apunte de vuelta (ida y vuelta con un click).
   - **Apagar**: necesario para romper el arco.
3. Entra al portal. Llegas **a un lado del controlador de destino**.

## Si no funciona (y por que)

| Sintoma | Causa | Solucion |
|---|---|---|
| "El Stargate no esta armado" | faltan anillos o el controlador no esta en el centro de la base | sigue las coordenadas y particulas que muestra el chat |
| Entras y "no pasa nada" | el destino no esta encendido o el portal no tiene destino | el chat lo dice; enciende ambos y elige destino |
| Te sube encima del controlador / "al techo" | (version anterior) portal sin destino; ahora se cancela y avisa | elige un destino |
| "Destino bloqueado: ... en x y z" | algo ocupa el bloque de llegada (un capacitor, una maquina, un muro) a **ambos** lados del controlador de destino | deja libre 1 de ancho x 2 de alto a cualquier lado |
| "El Stargate de destino ya no existe" | se rompio o se movio el controlador de destino | elige otro destino |
| Un solo sentido | solo uno apunta al otro | usa **Enlazar retorno** |

## Notas tecnicas
- La geometria vive en `StargateGeometry` (sin Bukkit, con tests); el comportamiento en `StargateController`.
- La direccion (hash) se asigna al **colocar** el controlador; los antiguos la reciben al abrir su menu.
- Datos por controlador en `BlockStorage`: `gfsgAddress`, `gfsgName`, `destination`, `dest`, `lit`, `locked`.
- El destino se guarda por UID de mundo (no por nombre): renombrar mundos no rompe los portales.
