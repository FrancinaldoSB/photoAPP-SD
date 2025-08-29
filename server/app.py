import socket
import os
import datetime
import struct
import tkinter as tk
from tkinter import messagebox
import tkinter.font as tkfont
from PIL import Image, ImageTk
import threading
import io

# Função para obter o endereço IP da máquina
def get_ip_address():
    try:
        # Cria um socket temporário para descobrir qual interface é usada para conexões externas
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))  # Conecta com o DNS do Google
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"  # Fallback para localhost

# Pasta para salvar as fotos
UPLOAD_FOLDER = 'uploads'
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

# Porta do servidor
PORT = 5000

# Variáveis globais para a interface gráfica
root = None
label_status = None
photo_label = None
img_reference = None  # Referência global para a imagem

# Inicializa a interface Tkinter
def init_gui():
    global root, label_status, photo_label
    
    root = tk.Tk()
    root.title("Servidor de Fotos")
    root.geometry("600x500")

    # Cor roxo pastel
    root.configure(bg="white")


    # Fonte Tilt Warp Regular (ou fallback)
    # Tenta Tilt Warp, senão Arial, senão padrão do sistema
    try:
        tiltwarp_font = tkfont.Font(family="Tilt Warp", size=22, weight="normal")
        # Testa se realmente existe
        if "tilt" not in tiltwarp_font.actual("family").lower():
            raise Exception()
    except:
        try:
            tiltwarp_font = tkfont.Font(family="Arial", size=22, weight="bold")
        except:
            tiltwarp_font = tkfont.Font(size=22, weight="bold")

    label_status = tk.Label(root, text="Aguardando foto!", font=tiltwarp_font, bg="white", fg="#3d246c")
    label_status.pack(pady=20)

    photo_label = tk.Label(root, bg="white")
    photo_label.pack(fill=tk.BOTH, expand=True, padx=20, pady=20)
    
    # Iniciar uma thread para atualizar a interface periodicamente
    def update_gui():
        while True:
            root.update()
            root.update_idletasks()
            threading.Event().wait(0.1)  # Pequeno delay para não consumir CPU

    gui_thread = threading.Thread(target=update_gui, daemon=True)
    gui_thread.start()

# Função para exibir imagem recebida
def show_image(image_path):
    global label_status, photo_label, img_reference
    
    try:
        img = Image.open(image_path)
        img.thumbnail((550, 400))  # Redimensiona para caber na tela
        
        # Guardar referência à imagem para evitar coleta de lixo
        img_reference = ImageTk.PhotoImage(img)
        
        # Atualizar interface em thread segura
        def update_ui():
            if label_status and photo_label:
                label_status.config(text="Foto Recebida!", fg="#4a148c")
                photo_label.config(image=img_reference)
        
        # Usar método after para programar a atualização da UI na thread principal
        if root:
            root.after(100, update_ui)
            
    except Exception as e:
        print(f"ERRO ao exibir imagem: {str(e)}")

# Função para processar clientes
def handle_client(client_socket, client_address):
    try:
        print(f"Nova conexão de {client_address[0]}:{client_address[1]}")
        
        # Recebe os primeiros 4 bytes que indicam o tamanho da imagem
        size_data = client_socket.recv(4)
        if len(size_data) != 4:
            print(f"Erro ao receber tamanho da imagem de {client_address[0]}")
            return
        
        # Converte os 4 bytes para um inteiro (tamanho da imagem)
        image_size = struct.unpack("!I", size_data)[0]
        print(f"Tamanho da imagem a receber: {image_size} bytes")
        
        # Recebe os bytes da imagem
        image_data = b''
        bytes_received = 0

        # Recebe a imagem em partes
        while bytes_received < image_size:
            chunk = client_socket.recv(min(4096, image_size - bytes_received))
            if not chunk:
                break
            image_data += chunk
            bytes_received += len(chunk)
            
            # Mostra progresso
            progress = (bytes_received / image_size) * 100
            print(f"\rRecebendo: {progress:.1f}%", end="")
        
        print("\nImagem recebida completamente!")
        
        # Gerar nome do arquivo com timestamp
        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        file_path = os.path.join(UPLOAD_FOLDER, f"photo_{timestamp}.jpg")
        
        # Salvar a imagem
        with open(file_path, "wb") as f:
            f.write(image_data)
            
        print(f"Imagem salva em: {file_path}")
        
        # Mostrar a imagem recebida
        show_image(file_path)
        
    except Exception as e:
        print(f"ERRO ao processar cliente {client_address[0]}: {str(e)}")
    finally:
        client_socket.close()

# Função principal para executar o servidor
def run_server():
    # Inicializa o socket do servidor
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    
    # Cores para terminal
    # Roxo pastel, azul pastel, amarelo pastel, verde pastel (cores aproximadas)
    PASTEL_PURPLE = "\033[38;2;179;157;219m"
    PASTEL_BLUE = "\033[38;2;144;202;249m"
    PASTEL_YELLOW = "\033[38;2;255;249;196m"
    PASTEL_GREEN = "\033[38;2;174;213;129m"
    WHITE = "\033[97m"
    BOLD = "\033[1m"
    END = "\033[0m"
    try:
        # Vincular o socket ao endereço e porta
        server_socket.bind(("", PORT))
        # Escutar por conexões
        server_socket.listen(5)

        ip_address = get_ip_address()

        # Tabela colorida (largura maior e alinhamento)
        table_w = 100
        sep = f"+{'-'*table_w}+"
        content_w = table_w  # para as bordas |
        def row(label, value, color=PASTEL_BLUE, label_color=WHITE):
            label_fmt = f" {label_color}{BOLD}{label:<22}{END}"
            # Garante que o valor não ultrapasse o limite
            value_str = str(value)
            max_value_len = content_w - 23  # 1 espaço + 22 label
            if len(value_str) > max_value_len:
                value_str = value_str[:max_value_len-3] + '...'
            value_fmt = f"{color}{value_str:<{max_value_len}}{END}"
            return f"{PASTEL_PURPLE}|{END}{label_fmt}{value_fmt}{PASTEL_PURPLE}|{END}"

        print(f"\n{PASTEL_PURPLE}{sep}{END}")
        print(f"{PASTEL_PURPLE}|{END}{BOLD}{PASTEL_YELLOW}{' SERVIDOR DE FOTOS INICIADO (Socket TCP) ':^{content_w}}{END}{PASTEL_PURPLE}|{END}")
        print(f"{PASTEL_PURPLE}{sep}{END}")
        print(row('Endereço IP local:', ip_address))
        print(row('Porta:', str(PORT)))
        print(row('URL completa:', f'{ip_address}:{PORT}'))
        print(row('Pasta de uploads:', os.path.abspath(UPLOAD_FOLDER)))
        print(row('No app Android, use:', f'{ip_address}:{PORT}', PASTEL_GREEN))
        print(f"{PASTEL_PURPLE}{sep}{END}\n")

        while True:
            # Aceitar conexão
            client_socket, client_address = server_socket.accept()
            # Criar thread para processar o cliente
            client_thread = threading.Thread(
                target=handle_client,
                args=(client_socket, client_address)
            )
            client_thread.daemon = True
            client_thread.start()
    except KeyboardInterrupt:
        print("\nServidor encerrado pelo usuário.")
    except Exception as e:
        print(f"ERRO no servidor: {str(e)}")
    finally:
        server_socket.close()

if __name__ == '__main__':
    # Inicializar a interface gráfica
    init_gui()
    
    # Iniciar o servidor em uma thread separada
    server_thread = threading.Thread(target=run_server, daemon=True)
    server_thread.start()
    
    # Inicia o loop principal do Tkinter na thread principal
    try:
        root.mainloop()
    except KeyboardInterrupt:
        print("\nPrograma encerrado pelo usuário.")
