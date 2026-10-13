package com.github.drakescraft_labs.galactifun.base.items;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometria del Stargate, sin depender de Bukkit (se puede probar con tests unitarios).
 *
 * <p>QUE HACE: define donde van los 15 bloques de anillo y los 21 del portal respecto al controlador, y en que
 * orientacion (plano vertical) puede estar construido.
 *
 * <p>POR QUE EXISTE: el original solo aceptaba UN plano (el anillo extendido a lo largo del eje Z) y, si el jugador lo
 * construia mirando al otro lado, respondia solo "The Stargate is not assembled!" sin decir por que. Los jugadores
 * (ver la conversacion de Discord de Galactifun) tenian que adivinar "tiene que estar alineado en el eje Z".
 * Ahora se aceptan los dos planos y se informa exactamente que bloques faltan.
 *
 * <p>Vista del anillo (desde delante, el controlador {@code C} es el centro de la fila inferior, y = 0):
 * <pre>
 *  y=6        R R R
 *  y=5      R . . . R          R = anillo   . = portal (21 bloques de END_GATEWAY)
 *  y=4    R . . . . . R        C = controlador (y=0, h=0)
 *  y=3    R . . . . . R        h = desplazamiento horizontal (-3..3)
 *  y=2    R . . . . . R
 *  y=1      R . . . R
 *  y=0        R C R
 * </pre>
 */
public final class StargateGeometry {

    /** Plano vertical en el que esta construido el anillo. La normal es el eje por el que se entra al portal. */
    public enum Orientation {
        /** Anillo extendido a lo largo del eje Z; se atraviesa por X (la orientacion original). */
        EJE_Z(0, 0, 1, 1, 0, 0, "Z-axis (north-south)"),
        /** Anillo extendido a lo largo del eje X; se atraviesa por Z. */
        EJE_X(1, 0, 0, 0, 0, 1, "X-axis (east-west)");

        private final int hx;
        private final int hy;
        private final int hz;
        private final int nx;
        private final int ny;
        private final int nz;
        private final String nombre;

        Orientation(int hx, int hy, int hz, int nx, int ny, int nz, String nombre) {
            this.hx = hx;
            this.hy = hy;
            this.hz = hz;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
            this.nombre = nombre;
        }

        /** Desplazamiento (dx, dy, dz) desde el controlador al bloque en (altura y, horizontal h). */
        public int[] offset(int y, int h) {
            return new int[] { hx * h, y, hz * h };
        }

        /** Vector unitario perpendicular al plano (hacia donde se sale del portal en el lado positivo). */
        public int[] normal() {
            return new int[] { nx, ny, nz };
        }

        public String nombre() {
            return nombre;
        }
    }

    /** Posicion de un bloque respecto al controlador: altura y desplazamiento horizontal. */
    public record Pos(int y, int h) {
    }

    public static final List<Pos> ANILLO = List.of(
            // base
            new Pos(0, 1), new Pos(0, -1),
            // esquinas
            new Pos(1, -2), new Pos(1, 2), new Pos(5, -2), new Pos(5, 2),
            // lado izquierdo
            new Pos(2, 3), new Pos(3, 3), new Pos(4, 3),
            // lado derecho
            new Pos(2, -3), new Pos(3, -3), new Pos(4, -3),
            // arriba
            new Pos(6, -1), new Pos(6, 0), new Pos(6, 1)
    );

    public static final List<Pos> PORTAL;

    static {
        List<Pos> p = new ArrayList<>();
        for (int h = -1; h <= 1; h++) {
            p.add(new Pos(1, h));
        }
        for (int y = 2; y <= 4; y++) {
            for (int h = -2; h <= 2; h++) {
                p.add(new Pos(y, h));
            }
        }
        for (int h = -1; h <= 1; h++) {
            p.add(new Pos(5, h));
        }
        PORTAL = List.copyOf(p);
    }

    private StargateGeometry() {
    }

    /** Devuelve las posiciones del anillo que NO cumplen la condicion (las que faltan). */
    public static List<Pos> faltantes(java.util.function.Predicate<Pos> presente) {
        List<Pos> faltan = new ArrayList<>();
        for (Pos pos : ANILLO) {
            if (!presente.test(pos)) {
                faltan.add(pos);
            }
        }
        return faltan;
    }

    /** Descripcion corta y entendible de una posicion del anillo, para mensajes al jugador. */
    public static String describir(Pos pos) {
        String altura;
        if (pos.y() == 0) {
            altura = "the base (next to the controller)";
        } else if (pos.y() == 6) {
            altura = "the top";
        } else {
            altura = "height " + pos.y();
        }
        String lado = pos.h() == 0 ? "center" : (pos.h() > 0 ? "side +" + pos.h() : "side " + pos.h());
        return altura + ", " + lado;
    }
}
