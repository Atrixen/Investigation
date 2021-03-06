

package com.iespacomolla.investigacion.dao.mysql.jdbc;

import com.iespacomolla.investigacion.dao.DAOException;
import com.iespacomolla.investigacion.dao.InvestigadorDAO;
import com.iespacomolla.investigacion.modelo.Campo;
import com.iespacomolla.investigacion.modelo.Investigador;
import com.iespacomolla.investigacion.modelo.Proyecto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;



public class mysqlInvestigadorJdbcDAO implements InvestigadorDAO{
    
    // Consultas básicas CRUD:
    final String INSERT = "INSERT INTO Investigador (nombre, proyecto_id, titulo, salario) VALUES (?, ?, ?, ?)";
    final String UPDATE = "UPDATE Investigador SET proyecto_id = ?, nombre =?, titulo=?, salario =? WHERE investigador_id = ?";
    final String DELETE = "DELETE FROM Investigador WHERE investigador_id = ?";
    final String GETONE = "SELECT * FROM Investigador WHERE investigador_id = ?";
    final String GETALL = "SELECT * FROM Investigador";
    // Consultas no tan básicas:
    final String GETCAMPOS = "SELECT c.*\n" +
                                "FROM Investigador i\n" +
                            "LEFT OUTER JOIN investigador_campo i_e\n" +
                                "ON i.investigador_id = i_e.investigador_id\n" +
                            "LEFT OUTER JOIN Campo AS c\n" +
                                "ON i_e.campo_id = c.campo_id\n" +
                            "WHERE i_e.investigador_id = ?;";
    
    
    private Connection con;
    private ResultSet rs;
    private PreparedStatement pStat;

    public mysqlInvestigadorJdbcDAO(Connection con){
        this.con = con; 
    }
    
    
    @Override
    public void insertar(Investigador o) throws DAOException{
        
        pStat = null;
        try{
            pStat = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            pStat.setString(1, o.getNombre());
            if (o.getProyecto() == null)
                pStat.setLong(2, 1);
            else
                pStat.setLong(2, o.getProyecto().getProyecto_id());
            pStat.setString(3, o.getTitulo());
            if (o.getSalario() == null)
                pStat.setDouble(4, 0.0);
            else
                pStat.setDouble(4, o.getSalario());
            
            if (pStat.executeUpdate() == 0)
                throw new DAOException("Puede que el objeto de inserción no haya sido persistido en la BD.");
            //Obtengo el id que me autogeneró el INSERT para el objeto:
            rs = pStat.getGeneratedKeys();
            if (rs.next())
                o.setInvestigador_id(rs.getLong(1));
            else
                throw new DAOException("Fallo al asignar ID a este Campo.");
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL INSERT ", ex);
        }
        finally{
            
            cerrarBasura();
        }
    }

    @Override
    public void modificar(Investigador o) throws DAOException{
        pStat = null;
        try{
            pStat = con.prepareStatement(UPDATE);
            if (o.getProyecto() == null)
                pStat.setLong(1, 1);
            else
                pStat.setLong(1, o.getProyecto().getProyecto_id());
            pStat.setString(2, o.getNombre());
            pStat.setString(3, o.getTitulo());
            pStat.setDouble(4, o.getSalario());
            pStat.setLong(5, o.getInvestigador_id());
            
            if (pStat.executeUpdate() == 0)
                throw new DAOException("Puede que no se haya modificado la fila en la BD.");
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL INSERT ", ex);
        }
        finally{
            if (pStat != null){
                try{
                   pStat.close();
                }catch (SQLException ex){
                    throw new DAOException("Error en SQL INSERT", ex);
                }
            }
        }
    }

    @Override
    public void eliminar(Investigador o) throws DAOException{
        PreparedStatement pStat = null;
        try{
            pStat = con.prepareStatement(DELETE);
            pStat.setLong(1, o.getInvestigador_id());
            
            if (pStat.executeUpdate() == 0)
                throw new DAOException("Puede que el Investigador no haya sido eliminado de la BD.");
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL INSERT ", ex);
        }
        finally{
            if (pStat != null){
                try{
                   pStat.close();
                }catch (SQLException ex){
                    throw new DAOException("Error en SQL INSERT", ex);
                }
            }
        }
    }
    
    //Transforma los valores de ResultSet en un objeto:
    private Investigador conversion(int level) throws DAOException{
        ResultSet rs2 = null;
        try{
            long id = rs.getLong("investigador_id");
            //En mi BD, la relación se refleja en el campo FK, para java debo obtener un objeto proyecto:
            mysqlProyectoJdbcDAO pDao = new mysqlProyectoJdbcDAO(con);
            Proyecto proyDeInvest = pDao.obtener(rs.getLong("proyecto_id"),level);
            String nombre = rs.getString("nombre");
            String titulo = rs.getString("titulo");
            Double salario = rs.getDouble("salario");
            //En mi BD, la relación se refleja con una tabla asociativa, pero necesito que este Investigador tenga una Lista 
            //de los campos en los que se especializa:
            List<Campo> campos = new ArrayList();
            pStat = null; //Ya no necesito el PreparedStatemen

            try{
                mysqlCampoJdbcDAO cDao = new mysqlCampoJdbcDAO(con);
                pStat = con.prepareStatement(GETCAMPOS);
                pStat.setLong(1, id);
                rs2 = pStat.executeQuery();
                
                while (rs2.next()){
                    if (level < 1)
                        campos.add(cDao.obtener(rs2.getLong("campo_id"),1));
                    else
                        campos.add(null);
                }
                }catch (SQLException ex){
                    throw new DAOException("Error al obtener los campos del investigador. ",ex);
                }
        
            Investigador i = new Investigador(nombre, titulo, salario, proyDeInvest, campos);
            i.setInvestigador_id(id);
            return i;
        }
        catch(SQLException ex){
            throw new DAOException("Error al obtener datos del Investigador",ex);
        }
        finally{
            if (rs2 != null){
                try {
                    rs2.close();
                } catch (SQLException ex) {
                    throw new DAOException("Error al terminar de obtener datos del Investigador",ex);
                }
            }
        }
    }
    
    @Override
    public Investigador obtener(Long id, int level) throws DAOException{
        pStat = null;
        Investigador c = null;
        rs = null;
        try{
            pStat = con.prepareStatement(GETONE);
            pStat.setLong(1, id);
            rs = pStat.executeQuery();
            
            if (rs.next())
                c = conversion(level);
            else{
                throw new DAOException("No se ha encontrado el registro de la tabla Campo");
            }     
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL GETONE ", ex);
        }
        finally{
            cerrarBasura();
        }
        return c;
    }

    @Override
    public List<Investigador> obtenerTodos() throws DAOException{
        pStat = null;
        List<Investigador> investigadores = new ArrayList();
        rs = null;
        try{
            pStat = con.prepareStatement(GETALL);
            rs = pStat.executeQuery();
            
            while (rs.next())
                // obtenerTodos siempre tiene en level 0
                investigadores.add(conversion(0));    
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL GETALL ", ex);
        }
        finally{   
            cerrarBasura();
        }
        return investigadores;
    }
    
    //Comprueba y cierra recursos
    private void cerrarBasura() throws DAOException{
        
        if (pStat != null){
                try{
                   pStat.close();
                }catch (SQLException ex){
                    throw new DAOException("Error al cerrar PreparedStatement. ", ex);
                }
            }
            if (rs != null){
                try{
                    rs.close();
                }catch(SQLException ex){
                    new DAOException("Error al cerrar ResultSet. ",ex);
                }
            }
    }
}
